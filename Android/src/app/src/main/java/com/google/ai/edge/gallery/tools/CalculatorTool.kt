/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ai.edge.gallery.tools

/**
 * Deterministic arithmetic evaluator + "is this even a math question" detector.
 *
 * Hand-rolled recursive-descent parser — no `eval()`/scripting engine, so there's no
 * code-execution surface regardless of input. Small on-device models are unreliable at
 * arithmetic; this guarantees a correct answer and skips the model entirely for a clear math
 * query. See `LlmChatViewModelBase.tryHandleQuickTools`.
 */
object CalculatorTool {

  sealed class Result {
    data class Success(val expression: String, val value: Double) : Result()
    data class Error(val expression: String, val reason: String) : Result()
  }

  // Optional leading phrase a user might say before the actual expression.
  private val LEADING_PHRASE = Regex(
    "^(?:calculate|compute|solve|what(?:'s| is))\\s*:?\\s*",
    RegexOption.IGNORE_CASE,
  )

  // "X% of Y" / "X percent of Y" -> rewritten to (X/100)*Y before the charset check.
  private val PERCENT_OF = Regex(
    "([0-9]+(?:\\.[0-9]+)?)\\s*(?:%|percent)\\s*of\\s*([0-9]+(?:\\.[0-9]+)?)",
    RegexOption.IGNORE_CASE,
  )

  // After stripping the leading phrase and rewriting percent-of, what remains must be PURE
  // arithmetic — this is the confidence gate that keeps ordinary prose ("I have 2 kids and 3
  // dogs") from being misread as a calculation.
  private val ARITHMETIC_CHARSET = Regex("^[0-9+\\-*/^%.() \\t]+$")
  private val HAS_OPERATOR = Regex("[+\\-*/^%]")
  private val HAS_DIGIT = Regex("[0-9]")

  /**
   * Attempts to read [rawInput] as a calculation. Returns null if it doesn't look like math at
   * all (caller falls through to normal chat) — [Result.Error] if it DOES look like math but
   * fails to evaluate (e.g. division by zero), so that case still gets an honest answer instead
   * of being silently forwarded to the model.
   */
  fun tryHandle(rawInput: String): Result? {
    val stripped = LEADING_PHRASE.replace(rawInput.trim(), "").trim()
    if (stripped.isEmpty()) return null

    val rewritten = PERCENT_OF.replace(stripped) { m ->
      "(${m.groupValues[1]}/100)*(${m.groupValues[2]})"
    }
    val cleaned = rewritten.trimEnd('.', '?', '!')

    if (!ARITHMETIC_CHARSET.matches(cleaned)) return null
    if (!HAS_OPERATOR.containsMatchIn(cleaned)) return null
    if (!HAS_DIGIT.containsMatchIn(cleaned)) return null

    return try {
      val value = evaluate(cleaned)
      Result.Success(expression = stripped, value = value)
    } catch (e: Exception) {
      Result.Error(expression = stripped, reason = e.message ?: "invalid expression")
    }
  }

  /** Formats a Double as an integer when it has no fractional part, else trims to 6dp. */
  fun formatValue(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return "%.6f".format(value).trimEnd('0').trimEnd('.')
  }

  // ---------------------------------------------------------------------------
  // Recursive-descent evaluator:
  //   expr    := term (('+'|'-') term)*
  //   term    := power (('*'|'/'|'%') power)*
  //   power   := unary ('^' power)?      (right-associative)
  //   unary   := ('-'|'+')? primary
  //   primary := NUMBER | '(' expr ')'
  // ---------------------------------------------------------------------------
  private fun evaluate(expr: String): Double {
    val parser = Parser(expr)
    val result = parser.parseExpr()
    parser.skipWhitespace()
    if (!parser.atEnd()) throw ArithmeticException("unexpected character at ${parser.pos}")
    return result
  }

  private class Parser(private val s: String) {
    var pos = 0
      private set

    fun atEnd() = pos >= s.length

    fun skipWhitespace() {
      while (!atEnd() && s[pos].isWhitespace()) pos++
    }

    private fun peek(): Char? {
      skipWhitespace()
      return if (atEnd()) null else s[pos]
    }

    fun parseExpr(): Double {
      var value = parseTerm()
      while (true) {
        when (peek()) {
          '+' -> { pos++; value += parseTerm() }
          '-' -> { pos++; value -= parseTerm() }
          else -> return value
        }
      }
    }

    private fun parseTerm(): Double {
      var value = parsePower()
      while (true) {
        when (peek()) {
          '*' -> { pos++; value *= parsePower() }
          '/' -> {
            pos++
            val divisor = parsePower()
            if (divisor == 0.0) throw ArithmeticException("division by zero")
            value /= divisor
          }
          '%' -> {
            pos++
            val divisor = parsePower()
            if (divisor == 0.0) throw ArithmeticException("division by zero")
            value %= divisor
          }
          else -> return value
        }
      }
    }

    private fun parsePower(): Double {
      val base = parseUnary()
      if (peek() == '^') {
        pos++
        val exponent = parsePower() // right-associative
        return Math.pow(base, exponent)
      }
      return base
    }

    private fun parseUnary(): Double =
      when (peek()) {
        '-' -> { pos++; -parseUnary() }
        '+' -> { pos++; parseUnary() }
        else -> parsePrimary()
      }

    private fun parsePrimary(): Double {
      if (peek() == '(') {
        pos++
        val value = parseExpr()
        if (peek() != ')') throw ArithmeticException("missing closing parenthesis")
        pos++
        return value
      }
      val start = pos
      skipWhitespace()
      val numStart = pos
      while (!atEnd() && (s[pos].isDigit() || s[pos] == '.')) pos++
      if (pos == numStart) throw ArithmeticException("expected a number at $start")
      return s.substring(numStart, pos).toDoubleOrNull()
        ?: throw ArithmeticException("invalid number at $numStart")
    }
  }
}

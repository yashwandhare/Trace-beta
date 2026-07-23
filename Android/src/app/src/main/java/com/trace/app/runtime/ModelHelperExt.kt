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

package com.trace.app.runtime

import com.trace.app.data.Model
import com.trace.app.data.RuntimeType
import com.trace.app.runtime.aicore.AICoreModelHelper
import com.trace.app.ui.llmchat.LlmChatModelHelper

var testingModelHelper: LlmModelHelper? = null

val Model.runtimeHelper: LlmModelHelper
  get() {
    testingModelHelper?.let {
      return it
    }
    if (this.runtimeType == RuntimeType.AICORE) {
      return AICoreModelHelper
    }
    return LlmChatModelHelper
  }

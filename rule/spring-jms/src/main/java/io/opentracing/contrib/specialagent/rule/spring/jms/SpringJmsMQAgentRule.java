/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.spring.jms;

import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class SpringJmsMQAgentRule extends AgentRule {
  @Override
  public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
    return builder
      .type(not(isInterface()).and(hasSuperType(named("org.springframework.jms.listener.SessionAwareMessageListener"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(SpringJmsMQAgentRule.class).on(named("onMessage").and(takesArguments(2))));
        }})
      .type(hasSuperType(named("org.springframework.jms.listener.AbstractPollingMessageListenerContainer")))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(advice(typeDescription).to(ReceiveMessage.class).on(named("receiveMessage").and(takesArguments(1))));
        }});
  }

  public static class ReceiveMessage {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object consumer, final @Advice.Return Object message) {
      if (isAllowed(className, origin))
        SpringJmsAgentIntercept.onReceiveMessage(consumer, message);
    }
  }

  @Advice.OnMethodEnter
  public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object message) {
    if (isAllowed(className, origin))
      SpringJmsAgentIntercept.onMessageEnter(message);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
    if (isAllowed(className, origin))
      SpringJmsAgentIntercept.onMessageExit(thrown);
  }
}
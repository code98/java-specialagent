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

package io.opentracing.contrib.specialagent.rule.mule4.service.http;

import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.EarlyReturnException;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class MuleFilterChainAgentRule extends AgentRule {
  @Override
  public AgentBuilder[] buildAgentUnchained(final AgentBuilder builder) {
    final Narrowable narrowable = builder.type(named("org.glassfish.grizzly.filterchain.FilterChainBuilder$StatelessFilterChainBuilder"));
    return new AgentBuilder[] {
      narrowable
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(advice(typeDescription).to(OnEnter.class).on(named("build")));
          }}),
      narrowable
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(advice(typeDescription).to(OnExit.class).on(named("build")));
          }})};
  }

  public static class OnEnter {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.This Object thiz) throws EarlyReturnException {
      if (!isAllowed(className, origin))
        return;

      final Object filterChain = MuleFilterChainAgentIntercept.enter(thiz);
      if (filterChain != null)
        throw new EarlyReturnException(filterChain);
    }
  }

  public static class OnExit {
    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) Throwable thrown) {
      if (thrown instanceof EarlyReturnException) {
        returned = ((EarlyReturnException)thrown).getReturnValue();
        thrown = null;
      }
    }
  }
}
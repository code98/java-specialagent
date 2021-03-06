/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.netty;

import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.utility.JavaModule;

public class NettyAgentRule extends AgentRule {
  @Override
  public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
    return builder
      .type(not(isInterface()).and(hasSuperType(named("io.netty.channel.ChannelPipeline"))))
      .transform(new Transformer() {
          @Override
          public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
            return builder.visit(advice(typeDescription).to(ChannelPipelineAdd.class).on(nameStartsWith("add").and(takesArgument(2, named("io.netty.channel.ChannelHandler")))));
        }});
  }

  public static class ChannelPipelineAdd {
    @Advice.OnMethodExit
    public static void exit(final @ClassName String className, final @Advice.Origin String origin, final @Advice.This Object thiz, final @Advice.Argument(value = 2, optional = true) Object arg2) {
      if (isAllowed(className, origin))
        NettyAgentIntercept.pipelineAddExit(thiz, arg2);
    }
  }
}
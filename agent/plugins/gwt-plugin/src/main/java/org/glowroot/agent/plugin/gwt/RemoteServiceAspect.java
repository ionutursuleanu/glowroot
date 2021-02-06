/*
 * Copyright 2017-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.plugin.gwt;

import org.glowroot.agent.plugin.api.*;
import org.glowroot.agent.plugin.api.ThreadContext.Priority;
import org.glowroot.agent.plugin.api.ThreadContext.ServletRequestInfo;
import org.glowroot.agent.plugin.api.checker.Nullable;
import org.glowroot.agent.plugin.api.config.BooleanProperty;
import org.glowroot.agent.plugin.api.weaving.*;

import java.lang.reflect.Method;
import java.net.URI;

public class RemoteServiceAspect {

	private static final BooleanProperty useAltTransactionNaming =
			Agent.getConfigService("gwt").getBooleanProperty("useAltTransactionNaming");

	@Shim("com.google.gwt.user.server.rpc.RPCRequest")
	public interface RPCRequest {
		@Nullable
		Method getMethod();
	}

	@Pointcut(className = "com.google.gwt.user.server.rpc.RemoteServiceServlet",
			methodName = "processCall", methodParameterTypes = { "com.google.gwt.user.server.rpc.RPCRequest" },
			timerName = "gwt rpc", nestingGroup = "gwt")
	public static class ResourceAdvice {

		private static final TimerName timerName = Agent.getTimerName(ResourceAdvice.class);

		@OnBefore
		public static TraceEntry onBefore(ThreadContext context,
				@BindParameter RPCRequest rpcRequest) {
			if (useAltTransactionNaming.value()) {
				context.setTransactionName(getAltTransactionName(rpcRequest.getMethod()),
						Priority.CORE_PLUGIN);
			} else {
				String transactionName = getTransactionName(context.getServletRequestInfo(),
						rpcRequest.getMethod());
				context.setTransactionName(transactionName, Priority.CORE_PLUGIN);
			}
			return context.startTraceEntry(MessageSupplier.create("gwt rpc: {}.{}()",
					rpcRequest.getMethod().getDeclaringClass().getName(), rpcRequest.getMethod().getName()),
					timerName);
		}

		@OnReturn
		public static void onReturn(@BindTraveler TraceEntry traceEntry) {
			traceEntry.end();
		}

		@OnThrow
		public static void onThrow(@BindThrowable Throwable t,
				@BindTraveler TraceEntry traceEntry) {
			traceEntry.endWithError(t);
		}

		private static String getTransactionName(@Nullable ServletRequestInfo servletRequestInfo,
				Method rpcMethod) {
			String methodName = rpcMethod.getName();
			if (servletRequestInfo == null) {
				return '#' + methodName;
			}
			String uri = servletRequestInfo.getUri();
			return "GWT " + uri + '#' + methodName;
		}

		private static String getAltTransactionName(Method rpcMethod) {
			String className = rpcMethod.getDeclaringClass().getSimpleName();
			String methodName = rpcMethod.getName();
			return className + '#' + methodName;
		}
	}
}

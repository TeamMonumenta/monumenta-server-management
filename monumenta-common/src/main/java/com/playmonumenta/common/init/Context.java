package com.playmonumenta.common.init;

import com.google.errorprone.annotations.FormatMethod;
import com.playmonumenta.common.init.annotations.ConfigPath;
import com.playmonumenta.common.init.annotations.Init;
import com.playmonumenta.common.init.annotations.Optional;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Nullable;

public class Context {
	@FunctionalInterface
	private interface ServiceFactory {
		@Nullable Object create(Context ctx);
	}

	@FunctionalInterface
	private interface ReflectiveCallable {
		Object call() throws ReflectiveOperationException;
	}

	@FunctionalInterface
	private interface ParamResolver {
		Object resolve(List<Object> values, Context ctx);
	}

	private record Request(Class<?> type, boolean optional) {
	}

	private record ParamPlan(List<Request> requests, ParamResolver resolver) {
		Object resolve(Context ctx) {
			final var values = requests.stream()
				.map(r -> r.optional() ? ctx.tryResolveInstance(r.type()) : ctx.resolveInstance(r.type()))
				.toList();
			return resolver.resolve(values, ctx);
		}
	}

	private record ServiceEntry(Executable exec, ParamPlan[] plans, ServiceFactory factory,
								@Nullable Class<?> serviceType) {
	}

	private record ProviderIndex(
		Map<Class<?>, Class<?>> provideMap,
		Map<Class<?>, Executable> serviceTypeToProvider
	) {
		@Nullable Executable resolve(Class<?> type) {
			return serviceTypeToProvider.get(provideMap.getOrDefault(type, type));
		}

		Stream<Executable> resolveAll(Class<?> type) {
			return serviceTypeToProvider.entrySet().stream()
				.filter(e -> type.isAssignableFrom(e.getKey()))
				.map(Map.Entry::getValue);
		}
	}

	private enum ParamKind {
		CONFIG_PATH {
			@Override
			ParamPlan planFor(Parameter p, ProviderIndex idx, InitPhase phase) {
				final var annotation = p.getAnnotation(ConfigPath.class);
				return new ParamPlan(
					List.of(new Request(Plugin.class, false)),
					(values, ctx) -> ((Plugin) values.getFirst()).getDataFolder().toPath().resolve(annotation.value())
				);
			}
		},
		ALL {
			@Override
			ParamPlan planFor(Parameter p, ProviderIndex idx, InitPhase phase) {
				final var typeArg = effectiveDependencyType(p);
				final var requests = idx.resolveAll(typeArg)
					.filter(dep -> phaseOf(dep).compareTo(phase) <= 0)
					.map(Context::serviceTypeOf)
					.filter(Objects::nonNull)
					.map(t -> new Request(t, true))
					.toList();
				return new ParamPlan(requests, (values, ctx) -> {
					final List<Object> present = values.stream().filter(Objects::nonNull).toList();
					return (All<?>) () -> present;
				});
			}
		},
		LATE {
			@Override
			ParamPlan planFor(Parameter p, ProviderIndex idx, InitPhase phase) {
				final var typeArg = effectiveDependencyType(p);
				if (p.isAnnotationPresent(Optional.class)) {
					return new ParamPlan(List.of(), (values, ctx) -> {
						@SuppressWarnings("NullAway") final Late<?> late = () -> ctx.tryResolveInstance(typeArg);
						return late;
					});
				}
				return new ParamPlan(List.of(), (values, ctx) -> (Late<?>) () -> ctx.resolveInstance(typeArg));
			}
		},
		OPTIONAL {
			@Override
			ParamPlan planFor(Parameter p, ProviderIndex idx, InitPhase phase) {
				return new ParamPlan(
					List.of(new Request(p.getType(), true)),
					(values, ctx) -> values.getFirst()
				);
			}
		},
		NORMAL {
			@Override
			ParamPlan planFor(Parameter p, ProviderIndex idx, InitPhase phase) {
				return new ParamPlan(
					List.of(new Request(p.getType(), false)),
					(values, ctx) -> values.getFirst()
				);
			}
		};

		abstract ParamPlan planFor(Parameter p, ProviderIndex idx, InitPhase phase);

		static ParamKind of(Parameter param) {
			if (param.isAnnotationPresent(ConfigPath.class)) {
				return CONFIG_PATH;
			}

			if (param.getType() == All.class) {
				return ALL;
			}

			if (param.getType() == Late.class) {
				return LATE;
			}

			if (param.isAnnotationPresent(Optional.class)) {
				return OPTIONAL;
			}
			return NORMAL;
		}
	}

	public static class Builder {
		private final Map<Class<?>, Class<?>> mProvideMap = new HashMap<>();
		private final List<Class<? extends AutoInit>> mAdditionalClasses = new ArrayList<>();
		private final List<String> mConditionalClassNames = new ArrayList<>();

		private Builder() {
		}

		public <T, U extends T> Builder provides(Class<T> provided, Class<U> provider) {
			mProvideMap.put(provided, provider);
			return this;
		}

		public Builder add(Class<? extends AutoInit> clazz) {
			mAdditionalClasses.add(clazz);
			return this;
		}

		/**
		 * Attempts to load {@code className} at build time and add it to the init chain. If the class or any of its
		 * dependencies cannot be found (e.g. because an optional plugin is not installed), it is silently skipped.
		 * Use this instead of {@link #add} for classes that reference optional-plugin types, since a class literal
		 * would trigger eager JVM resolution and throw before any runtime check could run.
		 */
		public Builder addIfPresent(String className) {
			mConditionalClassNames.add(className);
			return this;
		}

		/**
		 * Builds and validates the context object, checking for cycles. Note that classes may speculatively reference
		 * objects not registered as a service at a particular phase, instead depending on injected objects. Cycles
		 * however, are not permitted, nor can injected objects be used to work around phase issues.
		 *
		 * @return the context object
		 */
		@SuppressWarnings("EmptyCatch")
		public Context build() {
			final var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
			final var serviceLoader = ServiceLoader.load(AutoInit.class, caller.getClassLoader());

			final var allExecs = new LinkedHashSet<Executable>();

			serviceLoader.stream()
				.map(ServiceLoader.Provider::type)
				.forEach(clazz -> allExecs.addAll(findInitExecutables(clazz)));

			mAdditionalClasses.forEach(clazz -> allExecs.addAll(findInitExecutables(clazz)));

			for (final var name : mConditionalClassNames) {
				try {
					final var clazz = Class.forName(name, true, caller.getClassLoader()).asSubclass(AutoInit.class);
					allExecs.addAll(findInitExecutables(clazz));
				} catch (ClassNotFoundException | NoClassDefFoundError ignored) {
				}
			}

			final var index = new ProviderIndex(mProvideMap, buildServiceTypeMap(allExecs));

			final var plansByExec = new HashMap<Executable, ParamPlan[]>(allExecs.size());

			for (final var exec : allExecs) {
				final var phase = phaseOf(exec);
				plansByExec.put(exec, Arrays.stream(exec.getParameters())
					.map(p -> ParamKind.of(p).planFor(p, index, phase))
					.toArray(ParamPlan[]::new));
			}

			for (final var exec : allExecs) {
				final var consumerIsOptional = isOptional(exec);
				final var params = exec.getParameters();
				final var plans = Objects.requireNonNull(plansByExec.get(exec));

				for (int i = 0; i < params.length; i++) {
					final var param = params[i];

					if (param.getType() == Late.class && !param.isAnnotationPresent(Optional.class)) {
						final var typeArg = effectiveDependencyType(param);
						if (index.resolve(typeArg) == null) {
							throw ise(
								"%s takes Late<%s> but no service of that type is registered in the context",
								exec.getDeclaringClass().getName(), typeArg.getName()
							);
						}
					}

					for (final var req : plans[i].requests()) {
						if (req.optional()) {
							continue;
						}

						final var dep = index.resolve(req.type());

						if (dep == null || !isOptional(dep)) {
							continue;
						}

						if (!consumerIsOptional) {
							throw ise(
								"%s depends on optional service from %s without marking the parameter @Optional or " +
									"being optional itself",
								exec.getDeclaringClass().getName(), dep.getDeclaringClass().getName()
							);
						}
					}
				}

				final var m = nonStaticMethod(exec);

				if (m != null) {
					final var thisDep = index.resolve(m.getDeclaringClass());

					if (thisDep != null && isOptional(thisDep) && !consumerIsOptional) {
						throw ise(
							"%s has a non-static @Init method but its 'this' dependency on %s is optional; the " +
								"method" +
								" " +
								"must also be optional",
							exec.getDeclaringClass().getName(), m.getDeclaringClass().getName()
						);
					}
				}
			}

			final var sorted = topoSort(allExecs, buildDependencyGraph(allExecs, index, plansByExec));

			final var lastRealPhase = Arrays.stream(InitPhase.values())
				.filter(p -> p != InitPhase.LAZY)
				.reduce((a, b) -> b)
				.orElseThrow();

			final var phasePlan = new EnumMap<InitPhase, List<ServiceEntry>>(InitPhase.class);

			for (final var phase : InitPhase.values()) {
				if (phase == InitPhase.LAZY) {
					continue;
				}

				final var phaseStream = phase == lastRealPhase
					? Stream.concat(
					sorted.stream().filter(exec -> phaseOf(exec) == lastRealPhase),
					sorted.stream().filter(exec -> phaseOf(exec) == InitPhase.LAZY))
					: sorted.stream().filter(exec -> phaseOf(exec) == phase);

				phasePlan.put(phase, phaseStream
					.map(exec -> {
						final var plans = Objects.requireNonNull(plansByExec.get(exec));
						return new ServiceEntry(exec, plans, factoryFor(exec, plans), serviceTypeOf(exec));
					})
					.toList());
			}

			return new Context(new HashMap<>(mProvideMap), phasePlan);
		}

		private static List<Executable> findInitExecutables(Class<?> clazz) {
			final var ctorMatches = Arrays.stream(clazz.getDeclaredConstructors())
				.filter(c -> c.isAnnotationPresent(Init.class))
				.toList();
			final var methodMatches = Arrays.stream(clazz.getDeclaredMethods())
				.filter(m -> m.isAnnotationPresent(Init.class))
				.toList();

			final List<Executable> annotated = new ArrayList<>();
			annotated.addAll(ctorMatches);
			annotated.addAll(methodMatches);

			if (!annotated.isEmpty()) {
				// Non-static methods need a 'this' instance; auto-include the constructor if not already annotated
				final boolean hasNonStatic = methodMatches.stream()
					.anyMatch(m -> !Modifier.isStatic(m.getModifiers()));

				if (hasNonStatic && ctorMatches.isEmpty()) {
					annotated.addFirst(autoDetectConstructor(clazz));
				}

				final var seenTypes = new HashMap<Class<?>, Executable>();

				for (final var exec : annotated) {
					final var type = serviceTypeOf(exec);
					if (type != null) {
						final var conflict = seenTypes.put(type, exec);
						if (conflict != null) {
							throw iae("Multiple @Init annotations on %s provide the same service type %s",
								clazz.getName(), type.getName());
						}
					}
				}

				return annotated;
			}

			return List.of(autoDetectConstructor(clazz));
		}

		private static Constructor<?> autoDetectConstructor(Class<?> clazz) {
			final var ctors = clazz.getDeclaredConstructors();

			if (ctors.length == 1) {
				return ctors[0];
			}

			try {
				return clazz.getDeclaredConstructor();
			} catch (NoSuchMethodException e) {
				throw iae(e, "Cannot determine @Init for %s: multiple constructors with no @Init annotation",
					clazz.getName());
			}
		}

		private static ServiceFactory factoryFor(Executable exec, ParamPlan[] plans) {
			exec.setAccessible(true);
			final var context = "Failed to construct " + exec.getDeclaringClass().getName();

			if (exec instanceof Constructor<?> ctor) {
				return ctx -> safeInvoke(
					context,
					() -> ctor.newInstance(Arrays.stream(plans).map(p -> p.resolve(ctx)).toArray())
				);
			}

			final var method = (Method) exec;

			if (Modifier.isStatic(method.getModifiers())) {
				return ctx -> safeInvoke(
					context,
					() -> method.invoke(null, Arrays.stream(plans).map(p -> p.resolve(ctx)).toArray())
				);
			}

			return ctx -> safeInvoke(
				context,
				() -> method.invoke(
					ctx.resolveInstance(method.getDeclaringClass()),
					Arrays.stream(plans).map(p -> p.resolve(ctx)).toArray()
				)
			);
		}

		private static Map<Class<?>, Executable> buildServiceTypeMap(Set<Executable> execs) {
			final var map = new HashMap<Class<?>, Executable>();

			for (final var exec : execs) {
				final var serviceType = serviceTypeOf(exec);

				if (serviceType == null) {
					continue;
				}

				final var existing = map.put(serviceType, exec);

				if (existing != null && !existing.equals(exec)) {
					throw ise("Multiple AutoInit executables provide service type %s: %s and %s",
						serviceType.getName(),
						existing.getDeclaringClass().getName(),
						exec.getDeclaringClass().getName());
				}
			}

			return map;
		}

		private static Map<Executable, List<Executable>> buildDependencyGraph(
			Set<Executable> nodes,
			ProviderIndex index,
			Map<Executable, ParamPlan[]> plansByExec
		) {
			return nodes.stream().collect(Collectors.<Executable, Executable, List<Executable>>toMap(
				exec -> exec,
				exec -> {
					final var paramDeps = Arrays.stream(Objects.requireNonNull(plansByExec.get(exec)))
						.flatMap(plan -> plan.requests().stream())
						.map(r -> index.resolve(r.type()))
						.filter(Objects::nonNull);

					final var m = nonStaticMethod(exec);

					if (m != null) {
						final var thisDep = index.resolve(m.getDeclaringClass());
						return thisDep != null
							? Stream.concat(paramDeps, Stream.of(thisDep)).toList()
							: paramDeps.toList();
					}

					return paramDeps.toList();
				}
			));
		}

		private static List<Executable> topoSort(
			Set<Executable> nodes,
			Map<Executable, List<Executable>> deps
		) {
			final var visited = new HashSet<Executable>();
			final var inStack = new HashSet<Executable>();
			final var result = new ArrayList<Executable>();
			for (final var node : nodes) {
				if (!visited.contains(node)) {
					topoVisit(node, deps, visited, inStack, result);
				}
			}
			return result;
		}

		private static void topoVisit(
			Executable exec,
			Map<Executable, List<Executable>> deps,
			Set<Executable> visited,
			Set<Executable> inStack,
			List<Executable> result
		) {
			visited.add(exec);
			inStack.add(exec);
			final var execPhase = phaseOf(exec);
			for (final var dep : deps.getOrDefault(exec, List.of())) {
				if (inStack.contains(dep)) {
					throw ise("Cycle detected in AutoInit dependencies involving: %s",
						dep.getDeclaringClass().getName());
				}
				final var depPhase = phaseOf(dep);
				if (depPhase.compareTo(execPhase) > 0) {
					throw ise(
						"%s (phase %s) depends on %s (phase %s), but %s initializes after %s",
						exec.getDeclaringClass().getName(), execPhase,
						dep.getDeclaringClass().getName(), depPhase,
						dep.getDeclaringClass().getName(), exec.getDeclaringClass().getName()
					);
				}
				if (!visited.contains(dep)) {
					topoVisit(dep, deps, visited, inStack, result);
				}
			}
			inStack.remove(exec);
			result.add(exec);
		}
	}

	private final Map<Class<?>, Class<?>> mProvideMap;
	private final Map<InitPhase, List<ServiceEntry>> mPhasePlan;
	private final Map<Class<?>, Object> mInstances;
	private final Set<Executable> mProcessed;
	private final List<Object> mConstructionOrder;
	private final List<Listener> mPendingListeners;
	private final LifecycleImpl mLifecycle;

	private Context(Map<Class<?>, Class<?>> provideMap, Map<InitPhase, List<ServiceEntry>> phasePlan) {
		mProvideMap = provideMap;
		mPhasePlan = phasePlan;
		mInstances = new HashMap<>();
		mProcessed = new HashSet<>();
		mConstructionOrder = new ArrayList<>();
		mPendingListeners = new ArrayList<>();
		mLifecycle = new LifecycleImpl();
		mInstances.put(Lifecycle.class, mLifecycle);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Obtains an instance of {@code clazz}
	 *
	 * @param clazz the type to obtain instance of
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(Class<T> clazz) {
		return (T) resolveInstance(clazz);
	}

	/**
	 * Performs the relevant initialization phase. This may throw an exception if required objects are not provided at
	 * this phase. This will eagerly construct all services registered for that phase.
	 *
	 * @param phase           the initialization phase
	 * @param injectedObjects the objects to inject at this phase
	 */
	public void init(InitPhase phase, Object... injectedObjects) {
		for (final var obj : injectedObjects) {
			mInstances.put(obj.getClass(), obj);
		}

		for (final var entry : mPhasePlan.getOrDefault(phase, List.of())) {
			if (mProcessed.contains(entry.exec())) {
				continue;
			}
			constructService(entry);
		}

		mLifecycle.runPhase(phase);
	}

	/**
	 * Shuts down the context. Any services annotated with {@link Disposable} will be closed.
	 */
	public void shutdown() {
		final var reversed = new ArrayList<>(mConstructionOrder);
		Collections.reverse(reversed);
		for (final var instance : reversed) {
			if (!(instance instanceof Disposable closeable)) {
				continue;
			}

			closeable.shutdown();
		}
	}

	/**
	 * Registers all listeners queued since the last call to this method, then clears the queue.
	 */
	public void registerListeners() {
		final var plugin = (Plugin) resolveInstance(Plugin.class);
		for (final var listener : mPendingListeners) {
			plugin.getServer().getPluginManager().registerEvents(listener, plugin);
		}
		mPendingListeners.clear();
	}

	private Object resolveInstance(Class<?> requestedType) {
		final var resolvedType = mProvideMap.getOrDefault(requestedType, requestedType);
		final var instance = mInstances.get(resolvedType);
		if (instance != null) {
			return instance;
		}
		throw ise("Cannot resolve dependency: %s", requestedType.getName());
	}

	private @Nullable Object tryResolveInstance(Class<?> requestedType) {
		final var resolvedType = mProvideMap.getOrDefault(requestedType, requestedType);
		return mInstances.get(resolvedType);
	}

	private void constructService(ServiceEntry entry) {
		mProcessed.add(entry.exec());

		// Optional services are skipped if any non-optional request cannot be satisfied
		if (isOptional(entry.exec())) {
			for (final var plan : entry.plans()) {
				for (final var req : plan.requests()) {
					if (!req.optional() && tryResolveInstance(req.type()) == null) {
						return;
					}
				}
			}
			final var m = nonStaticMethod(entry.exec());
			if (m != null && tryResolveInstance(m.getDeclaringClass()) == null) {
				return;
			}
		}

		final var instance = entry.factory().create(this);

		final var serviceType = entry.serviceType();
		if (serviceType == null || instance == null) {
			return;
		}

		mInstances.put(serviceType, instance);
		mConstructionOrder.add(instance);
		if (instance instanceof Listener l) {
			mPendingListeners.add(l);
		}
	}

	private static Class<?> effectiveDependencyType(Parameter param) {
		final var raw = param.getType();
		if (raw != Late.class && raw != All.class) {
			return raw;
		}
		final var typeArg = ((ParameterizedType) param.getParameterizedType()).getActualTypeArguments()[0];
		if (!(typeArg instanceof Class<?> c)) {
			throw iae("%s<?> type argument in %s must be a concrete class, not a wildcard or parameterized type",
				raw.getSimpleName(), param.getDeclaringExecutable().getDeclaringClass().getName());
		}
		return c;
	}

	private static @Nullable Init initAnnotation(Executable exec) {
		final var execInit = exec.getAnnotation(Init.class);
		return execInit != null ? execInit : exec.getDeclaringClass().getAnnotation(Init.class);
	}

	private static InitPhase phaseOf(Executable exec) {
		final var init = initAnnotation(exec);
		return init != null ? init.value() : InitPhase.LAZY;
	}

	private static boolean isOptional(Executable exec) {
		final var init = initAnnotation(exec);
		return init != null && init.optional();
	}

	private static @Nullable Method nonStaticMethod(Executable exec) {
		if (exec instanceof Method m && !Modifier.isStatic(m.getModifiers())) return m;
		return null;
	}

	private static @Nullable Class<?> serviceTypeOf(Executable exec) {
		if (exec instanceof Method method) {
			return method.getReturnType() == void.class ? null : method.getReturnType();
		}
		return exec.getDeclaringClass();
	}

	private static Object safeInvoke(String context, ReflectiveCallable callable) {
		try {
			return callable.call();
		} catch (InvocationTargetException e) {
			final var cause = e.getCause();

			if (cause instanceof RuntimeException re) {
				throw re;
			}

			if (cause instanceof Error err) {
				throw err;
			}
			throw new RuntimeException(context, cause);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(context, e);
		}
	}

	@FormatMethod
	private static IllegalArgumentException iae(@PrintFormat String fmt, Object... args) {
		return new IllegalArgumentException(fmt.formatted(args));
	}

	@FormatMethod
	private static IllegalArgumentException iae(Throwable cause, @PrintFormat String fmt, Object... args) {
		return new IllegalArgumentException(fmt.formatted(args), cause);
	}

	@FormatMethod
	private static IllegalStateException ise(@PrintFormat String fmt, Object... args) {
		return new IllegalStateException(fmt.formatted(args));
	}
}

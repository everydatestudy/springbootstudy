/**
 * Delegate for resolving constructors and factory methods.
 * <p>代表解析构造函数和工厂方法</p>
 * <p>Performs constructor resolution through argument matching.
 * <p>通过参数匹配执行构造函数解析</p>
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 2.0
 * @see #autowireConstructor
 * @see #instantiateUsingFactoryMethod
 * @see AbstractAutowireCapableBeanFactory
 */
class ConstructorResolver {

	private static final Object[] EMPTY_ARGS = new Object[0];

	/**
	 * Marker for autowired arguments in a cached argument array, to be later replaced
	 * by a {@linkplain #resolveAutowiredArgument resolved autowired argument}.
	 * 缓存的参数数组中自动装配的参数标记，以后将由解析的自动装配参数替换
	 */
	private static final Object autowiredArgumentMarker = new Object();

	private static final NamedThreadLocal<InjectionPoint> currentInjectionPoint =
			new NamedThreadLocal<>("Current injection point");


	private final AbstractAutowireCapableBeanFactory beanFactory;

	private final Log logger;


	/**
	 * Create a new ConstructorResolver for the given factory and instantiation strategy.
	 * @param beanFactory the BeanFactory to work with
	 */
	public ConstructorResolver(AbstractAutowireCapableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.logger = beanFactory.getLogger();
	}


	/**
	 * <p>以自动注入方式调用最匹配的构造函数来实例化参数对象:
	 *  <ol>
	 *   <li>新建一个BeanWrapperImp实例，用于封装使用工厂方法生成与beanName对应的Bean对象【变量 bw】</li>
	 *   <li>初始化bw</li>
	 *   <li>定义一个用于要适用的构造函数对象的Constructor对象【变量 constructorToUse】</li>
	 *   <li>声明一个用于存储不同形式的参数值的ArgumentsHolder，默认为null【变量 argsHolderToUse】</li>
	 *   <li>定义一个用于要使用的参数值数组 【变量 argsToUse】</li>
	 *   <li>如果explicitArgs不为null,让argsToUse引用explicitArgs</li>
	 *   <li>否则:
	 *    <ol>
	 *     <li>声明一个要解析的参数值数组，默认为null【变量 argsToResolve】</li>
	 *     <li>使用mbd的构造函数字段通用锁进行加锁，以保证线程安全:
	 *      <ol>
	 *       <li>指定constructorToUse引用mbd已解析的构造函数或工厂方法对象</li>
	 *       <li>如果constructorToUse不为null且 mbd已解析构造函数参数:
	 *        <ol>
	 *         <li>指定argsToUse引用mbd完全解析的构造函数参数值</li>
	 *         <li>如果argsToUse为null,指定argsToResolve引用mbd部分准备好的构造函数参数值</li>
	 *        </ol>
	 *       </li>
	 *       <li>如果argsToResolve不为null,即表示mbd还没有完全解析的构造函数参数值,解析缓存在mbd中准备好
	 *       的参数值,允许在没有此类BeanDefintion的时候回退</li>
	 *      </ol>
	 *     </li>
	 *    </ol>
	 *   </li>
	 *   <li>如果constructorToUse为null或者argsToUser为null:
	 *    <ol>
	 *     <li>让candidates引用chosenCtors【变量 candidates】</li>
	 *     <li>如果candidates为null:
	 *      <ol>
	 *       <li>获取mbd的Bean类【变量 beanClass】</li>
	 *       <li>如果mbd允许访问非公共构造函数和方法，获取BeanClass的所有声明构造函数;否则获取
	 *       public的构造函数</li>
	 *       <li>捕捉获取beanClass的构造函数发出的异常,抛出BeanCreationException</li>
	 *      </ol>
	 *     </li>
	 *     <li>如果candidateList只有一个元素 且 没有传入构造函数值 且 mbd也没有构造函数参数值:
	 *      <ol>
	 *       <li>获取candidates中唯一的方法【变量 uniqueCandidate】</li>
	 *       <li>如果uniqueCandidate不需要参数:
	 *        <ol>
	 *         <li>使用mdb的构造函数字段的通用锁【{@link RootBeanDefinition#constructorArgumentLock}】进行加锁以保证线程安全:
	 *          <ol>
	 *           <li>让mbd缓存已解析的构造函数或工厂方法【{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}】</li>
	 *           <li>让mbd标记构造函数参数已解析【{@link RootBeanDefinition#constructorArgumentsResolved}】</li>
	 *           <li>让mbd缓存完全解析的构造函数参数【{@link RootBeanDefinition#resolvedConstructorArguments}】</li>
	 *          </ol>
	 *         </li>
	 *         <li>使用constructorToUse生成与beanName对应的Bean对象,并将该Bean对象保存到bw中</li>
	 *         <li>将bw返回出去</li>
	 *        </ol>
	 *       </li>
	 *      </ol>
	 *     </li>
	 *     <li>定义一个mbd是否支持使用构造函数进行自动注入的标记. 如果chosenCtos不为null或者mbd解析自动注入模式为自动注入可以满足的最
	 *     贪婪的构造函数的常数(涉及解析适当的构造函数)就为true;否则为false 【变量 autowiring】</li>
	 *     <li>定义一个用于存放解析后的构造函数参数值的ConstructorArgumentValues对象【变量 resolvedValues】</li>
	 *     <li>定义一个最少参数数，默认为0【变量 minNrOfArgs】</li>
	 *     <li>如果explicitArgs不为null,minNrOfArgs引用explitArgs的数组长度</li>
	 *     <li>否则:
	 *      <ol>
	 *       <li>获取mbd的构造函数参数值【变量 cargs】</li>
	 *       <li>对resolvedValues实例化</li>
	 *       <li>将cargs解析后值保存到resolveValues中，并让minNrOfArgs引用解析后的最小(索引参数值数+泛型参数值数)</li>
	 *      </ol>
	 *     </li>
	 *     <li>对candidates进行排序</li>
	 *     <li>定义一个最小类型差异权重，默认是Integer最大值【变量 minTypeDiffWeight】</li>
	 *     <li>定义一个存储摸棱两可的工厂方法的Set集合,以用于抛出BeanCreationException时描述异常信息 【变量 ambiguousConstructors】</li>
	 *     <li>定义一个用于UnsatisfiedDependencyException的列表【变量 causes】</li>
	 *     <li>遍历candidates，元素名为candidate:
	 *      <ol>
	 *       <li>获取candidate的参数类型数组【变量 constructorToUse】</li>
	 *       <li>如果constructorToUse不为null且argsToUse不为null且argsToUse的数组长度大于paramTypes的数组长度。即意味着找到最匹配的构造函数：
	 *       跳出遍历循环
	 *       </li>
	 *       <li>如果paramTypes的数组长度小于minNrOfArgs,跳过当次循环中剩下的步骤，执行下一次循环。</li>
	 *       <li>定义一个封装参数数组的ArgumentsHolder对象【变量 argsHolder】</li>
	 *       <li>如果resolveValues不为null：
	 *        <ol>
	 *         <li>获取candidate的ConstructorProperties注解的name属性值作为candidate的参数名【变量 paramNames】</li>
	 *         <li>如果paramName为null
	 *          <ol>
	 *           <li>获取beanFactory的参数名发现器【变量 pnd】</li>
	 *           <li>如果pnd不为null,通过pnd解析candidate的参数名</li>
	 *          </ol>
	 *         </li>
	 *         <li>将resolvedValues转换成一个封装着参数数组ArgumentsHolder实例，当candidate只有一个时，支持可在抛
	 *         出没有此类BeanDefintion的异常返回null，而不抛出异常</li>
	 *         <li>捕捉UnsatisfiedDependencyException:
	 *          <ol>
	 *           <li>如果当前日志可打印跟踪级别的信息,打印跟踪级别日志：忽略bean'beanName'的构造函数[candidate]</li>
	 *           <li>如果cause为null,对cause进行实例化成LinkedList对象</li>
	 *           <li>将ex添加到causes中</li>
	 *           <li>跳过本次循环体中余下尚未执行的语句，立即进行下一次的循环</li>
	 *          </ol>
	 *         </li>
	 *        </ol>
	 *       </li>
	 *       <li>否则:
	 *        <ol>
	 *         <li>如果paramTypes数组长度于explicitArgs的数组长度不相等,否则跳过当次循环中剩下的步
	 *         骤，执行下一次循环</li>
	 *         <li>实例化argsHolder，封装explicitArgs到argsHolder</li>
	 *        </ol>
	 *       </li>
	 *       <li>如果bd支持的构造函数解析模式时宽松模式,引用获取类型差异权重值，否则引用获取
	 *       Assignabliity权重值</li>
	 *       <li>如果typeDiffWeight小于minTypeDiffWeight:
	 *        <ol>
	 *         <li>让constructorToUse引用candidate</li>
	 *         <li>让argsHolderToUse引用argsHolder</li>
	 *         <li>让argToUse引用argsHolder的经过转换后参数值数组</li>
	 *         <li>让minTypeDiffWeight引用typeDiffWeight</li>
	 *         <li>将ambiguousFactoryMethods置为null</li>
	 *        </ol>
	 *       </li>
	 *       <li>【else if】如果factoryMethodToUse不为null 且 typeDiffWeight与minTypeDiffWeight相等:
	 *        <ol>
	 *         <li>如果ambiguousFactoryMethods为null:
	 *          <ol>
	 *           <li>初始化ambiguousFactoryMethods为LinkedHashSet实例</li>
	 *           <li>将constructorToUse添加到ambiguousFactoryMethods中</li>
	 *          </ol>
	 *         </li>
	 *         <li>将candidate添加到ambiguousFactoryMethods中</li>
	 *        </ol>
	 *       </li>
	 *      </ol>
	 *     </li>
	 *     <li>如果constructorToUse为null:
	 *      <ol>
	 *       <li>如果causes不为null:
	 *        <ol>
	 *         <li>从cause中移除最新的UnsatisfiedDependencyException【变量 ex】</li>
	 *         <li>遍历causes,元素为cause:将cause添加到该Bean工厂的抑制异常列表【{@link DefaultSingletonBeanRegistry#suppressedExceptions}】 中</li>
	 *         <li>重新抛出ex</li>
	 *        </ol>
	 *       </li>
	 *       <li>抛出BeanCreationException：无法解析匹配的构造函数(提示：为简单参数指定索引/类型/名称参数,以避免类型歧义)</li>
	 *      </ol>
	 *     </li>
	 *     <li>【else if】如果ambiguousFactoryMethods不为null 且mbd是使用的是严格模式解析构造函数,抛出BeanCreationException</li>
	 *     <li>如果explicitArgs为null 且 argsHolderToUser不为null,将argsHolderToUse所得到的参数值属性缓存到mbd对应的属性中</li>
	 *    </ol>
	 *   </li>
	 *   <li>如果argsToUse为null，抛出异常：未解析的构造函数参数</li>
	 *   <li>使用factoryBean生成与beanName对应的Bean对象,并将该Bean对象保存到bw中</li>
	 *   <li>将bw返回出去</li>
	 *  </ol>
	 * </p>
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>"autowire constructor"(按类型带有构造函数参数)的行为。如果显示指定了构造函数自变量值，
	 * 则将所有剩余自变量与Bean工厂中的Bean进行匹配时也适用</p>
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * <p>这对应于构造函数注入：在这种模式下，Spring Bean工厂能够托管需要基于构造函数数的
	 * 依赖关系解析的组件</p>
	 * @param beanName the name of the bean -- Bean名
	 * @param mbd the merged bean definition for the bean -- Bean的BeanDefinition
	 * @param chosenCtors chosen candidate constructors (or {@code null} if none)  -- 选择的候选构造函数
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 *                     -- 用于构造函数或工厂方法调用的显示参数
	 * @return a BeanWrapper for the new instance -- 新实例的BeanWrapper
	 */
	public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
			@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {
		//新建一个BeanWrapperImp实例，用于封装使用工厂方法生成与beanName对应的Bean对象
		BeanWrapperImpl bw = new BeanWrapperImpl();
		//初始化bw
		this.beanFactory.initBeanWrapper(bw);

		//定义一个用于要适用的构造函数对象的Constructor对象
		Constructor<?> constructorToUse = null;
		//声明一个用于存储不同形式的参数值的ArgumentsHolder，默认为null
		ArgumentsHolder argsHolderToUse = null;
		//定义一个用于要使用的参数值数组
		Object[] argsToUse = null;

		//如果explicitArgs不为null
		if (explicitArgs != null) {
			//让argsToUse引用explicitArgs
			argsToUse = explicitArgs;
		}
		else {
			//声明一个要解析的参数值数组，默认为null
			Object[] argsToResolve = null;
			//使用mbd的构造函数字段通用锁进行加锁，以保证线程安全
			synchronized (mbd.constructorArgumentLock) {
				//指定constructorToUse引用mbd已解析的构造函数或工厂方法对象
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				//如果constructorToUse不为null且 mbd已解析构造函数参数
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached constructor...
					//找到了缓存的构造方法
					//指定argsToUse引用mbd完全解析的构造函数参数值
					argsToUse = mbd.resolvedConstructorArguments;
					//如果argsToUse为null
					if (argsToUse == null) {
						//指定argsToResolve引用mbd部分准备好的构造函数参数值
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			//如果argsToResolve不为null,即表示mbd还没有完全解析的构造函数参数值
			if (argsToResolve != null) {
				//解析缓存在mbd中准备好的参数值,允许在没有此类BeanDefintion的时候回退
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);
			}
		}

		//如果constructorToUse为null或者argsToUser为null
		if (constructorToUse == null || argsToUse == null) {
			// Take specified constructors, if any.
			// 采用指定的构造函数(如果有)
			//让candidates引用chosenCtors
			Constructor<?>[] candidates = chosenCtors;
			//如果candidates为null
			if (candidates == null) {
				//获取mbd的Bean类
				Class<?> beanClass = mbd.getBeanClass();
				try {
					//如果mbd允许访问非公共构造函数和方法，获取BeanClass的所有声明构造函数;否则获取public的构造函数
					candidates = (mbd.isNonPublicAccessAllowed() ?
							beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				}
				//捕捉获取beanClass的构造函数发出的异常
				catch (Throwable ex) {
					//抛出BeanCreationException：从ClassLoader[beanClass.getClassLoader()]解析Bean类[beanClass.getName()]上
					// 声明的构造函数失败
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Resolution of declared constructors on bean Class [" + beanClass.getName() +
							"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
				}
			}
			//如果candidateList只有一个元素 且 没有传入构造函数值 且 mbd也没有构造函数参数值
			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				//获取candidates中唯一的方法
				Constructor<?> uniqueCandidate = candidates[0];
				//如果uniqueCandidate不需要参数
				if (uniqueCandidate.getParameterCount() == 0) {
					//使用mdb的构造函数字段的通用锁【{@link RootBeanDefinition#constructorArgumentLock}】进行加锁以保证线程安全
					synchronized (mbd.constructorArgumentLock) {
						//让mbd缓存已解析的构造函数或工厂方法【{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}】
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						//让mbd标记构造函数参数已解析【{@link RootBeanDefinition#constructorArgumentsResolved}】
						mbd.constructorArgumentsResolved = true;
						//让mbd缓存完全解析的构造函数参数【{@link RootBeanDefinition#resolvedConstructorArguments}】
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					//使用constructorToUse生成与beanName对应的Bean对象,并将该Bean对象保存到bw中
					bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
					//将bw返回出去
					return bw;
				}
			}

			// Need to resolve the constructor.
			// 需要解析构造函数
			//定义一个mbd是否支持使用构造函数进行自动注入的标记. 如果chosenCtos不为null或者mbd解析自动注入模式为自动注入可以满足的最
			// 贪婪的构造函数的常数(涉及解析适当的构造函数)就为true;否则为false
			boolean autowiring = (chosenCtors != null ||
					mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			//定义一个用于存放解析后的构造函数参数值的ConstructorArgumentValues对象
			ConstructorArgumentValues resolvedValues = null;

			//定义一个最少参数数，默认为0
			int minNrOfArgs;
			//如果explicitArgs不为null
			if (explicitArgs != null) {
				//minNrOfArgs引用explitArgs的数组长度
				minNrOfArgs = explicitArgs.length;
			}
			else {
				//获取mbd的构造函数参数值
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
				//对resolvedValues实例化
				resolvedValues = new ConstructorArgumentValues();
				//将cargs解析后值保存到resolveValues中，并让minNrOfArgs引用解析后的最小(索引参数值数+泛型参数值数)
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}
			//AutowireUtils.sortConstructors:对给定的构造函数进行排序.优先取public级别的构造函数，然后取非public级别的构造函数；
			// 相同的访问级别，参数越多越靠
			//对candidates进行排序
			AutowireUtils.sortConstructors(candidates);
			//定义一个最小类型差异权重，默认是Integer最大值
			int minTypeDiffWeight = Integer.MAX_VALUE;
			//定义一个存储摸棱两可的工厂方法的Set集合,以用于抛出BeanCreationException时描述异常信息
			Set<Constructor<?>> ambiguousConstructors = null;
			//定义一个用于UnsatisfiedDependencyException的列表
			LinkedList<UnsatisfiedDependencyException> causes = null;

			//遍历candidates，元素名为candidate
			for (Constructor<?> candidate : candidates) {
				//获取candidate的参数类型数组
				Class<?>[] paramTypes = candidate.getParameterTypes();

				//如果已经找到要使用的构造函数和要使用的构造函数参数值且要使用的构造函数参数值比要匹配的参数长度要多，即意味着找到
				// 最匹配的构造函数
				//如果constructorToUse不为null且argsToUse不为null且argsToUse的数组长度大于paramTypes的数组长度
				if (constructorToUse != null && argsToUse != null && argsToUse.length > paramTypes.length) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					//已经发现可以满足的贪婪构造函数->不要再看了，只剩下更少的贪婪构造函数了
					//跳出遍历循环
					break;
				}
				//如果paramTypes的数组长度小于minNrOfArgs
				if (paramTypes.length < minNrOfArgs) {
					//跳过当次循环中剩下的步骤，执行下一次循环。
					continue;
				}
				//定义一个封装参数数组的ArgumentsHolder对象
				ArgumentsHolder argsHolder;
				//如果resolveValues不为null
				if (resolvedValues != null) {
					try {
						//获取candidate的ConstructorProperties注解的name属性值作为candidate的参数名
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, paramTypes.length);
						//如果paramName为null
						if (paramNames == null) {
							//获取beanFactory的参数名发现器
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							//如果pnd不为null
							if (pnd != null) {
								//通过pnd解析candidate的参数名
								paramNames = pnd.getParameterNames(candidate);
							}
						}
						//将resolvedValues转换成一个封装着参数数组ArgumentsHolder实例，当candidate只有一个时，支持可在抛
						// 出没有此类BeanDefintion的异常返回null，而不抛出异常
						argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
								getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
					}
					//捕捉UnsatisfiedDependencyException
					catch (UnsatisfiedDependencyException ex) {
						//如果当前日志可打印跟踪级别的信息
						if (logger.isTraceEnabled()) {
							//打印跟踪级别日志：忽略bean'beanName'的构造函数[candidate]
							logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
						}
						// Swallow and try next constructor.
						// 吞下并尝试下一个重载的构造函数
						//如果cause为null
						if (causes == null) {
							//对cause进行实例化成LinkedList对象
							causes = new LinkedList<>();
						}
						//将ex添加到causes中
						causes.add(ex);
						//跳过本次循环体中余下尚未执行的语句，立即进行下一次的循环
						continue;
					}
				}
				else {
					// Explicit arguments given -> arguments length must match exactly.
					// 给定的显示参数 -> 参数长度必须完全匹配
					//如果paramTypes数组长度于explicitArgs的数组长度不相等
					if (paramTypes.length != explicitArgs.length) {
						//跳过当次循环中剩下的步骤，执行下一次循环
						continue;
					}
					//实例化argsHolder，封装explicitArgs到argsHolder
					argsHolder = new ArgumentsHolder(explicitArgs);
				}
				//mbd支持的构造函数解析模式,默认使用宽松模式:
				// 1. 严格模式如果摸棱两可的构造函数在转换参数时都匹配，则抛出异常
				// 2. 宽松模式将使用"最接近类型匹配"的构造函数
				//如果bd支持的构造函数解析模式时宽松模式,引用获取类型差异权重值，否则引用获取Assignabliity权重值
				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				// Choose this constructor if it represents the closest match.
				// 如果它表示最接近的匹配项，则选择此构造函数
				// 如果typeDiffWeight小于minTypeDiffWeight
				if (typeDiffWeight < minTypeDiffWeight) {
					让constructorToUse引用candidate
					constructorToUse = candidate;
					//让argsHolderToUse引用argsHolder
					argsHolderToUse = argsHolder;
					//让argToUse引用argsHolder的经过转换后参数值数组
					argsToUse = argsHolder.arguments;
					//让minTypeDiffWeight引用typeDiffWeight
					minTypeDiffWeight = typeDiffWeight;
					//将ambiguousFactoryMethods置为null
					ambiguousConstructors = null;
				}
				//如果factoryMethodToUse不为null 且 typeDiffWeight与minTypeDiffWeight相等
				else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					//如果ambiguousFactoryMethods为null
					if (ambiguousConstructors == null) {
						//初始化ambiguousFactoryMethods为LinkedHashSet实例
						ambiguousConstructors = new LinkedHashSet<>();
						//将constructorToUse添加到ambiguousFactoryMethods中
						ambiguousConstructors.add(constructorToUse);
					}
					//将candidate添加到ambiguousFactoryMethods中
					ambiguousConstructors.add(candidate);
				}
			}

			//如果constructorToUse为null
			if (constructorToUse == null) {
				//如果causes不为null
				if (causes != null) {
					//从cause中移除最新的UnsatisfiedDependencyException
					UnsatisfiedDependencyException ex = causes.removeLast();
					//遍历causes,元素为cause
					for (Exception cause : causes) {
						//将cause添加到该Bean工厂的抑制异常列表【{@link DefaultSingletonBeanRegistry#suppressedExceptions】 中
						this.beanFactory.onSuppressedException(cause);
					}
					//重新抛出ex
					throw ex;
				}
				//抛出BeanCreationException：无法解析匹配的构造函数(提示：为简单参数指定索引/类型/名称参数,以避免类型歧义)
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Could not resolve matching constructor " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
			}
			//如果ambiguousFactoryMethods不为null 且mbd是使用的是严格模式解析构造函数
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				//抛出BeanCreationException：在bean'beanName'中找到的摸棱两可的构造函数匹配项(提示:为简单参数指定索引/类型/
				// 名称参数以避免类型歧义)：ambiguousFactoryMethods
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous constructor matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousConstructors);
			}

			//如果explicitArgs为null 且 argsHolderToUser不为null
			if (explicitArgs == null && argsHolderToUse != null) {
				//将argsHolderToUse所得到的参数值属性缓存到mbd对应的属性中
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}
		//如果argsToUse为null，抛出异常：未解析的构造函数参数
		Assert.state(argsToUse != null, "Unresolved constructor arguments");
		//使用factoryBean生成与beanName对应的Bean对象,并将该Bean对象保存到bw中
		bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
		//将bw返回出去
		return bw;
	}

	/**
	 * 使用constructorToUse生成与beanName对应的Bean对象:
	 * <ol>
	 *  <li>如果有安全管理器,使用特权方式运行：在beanFactory中返回beanName的Bean实例，并通过
	 *  factoryMethod创建它</li>
	 *  <li>否则：直接在beanFactory中返回beanName的Bean实例，并通过factoryMethod创建它</li>
	 *  <li>捕捉所有实例化对象过程中的异常,抛出BeanCreationException</li>
	 * </ol>
	 * @param beanName  要生成的Bean对象所对应的bean名
	 * @param mbd  beanName对于的合并后RootBeanDefinition
	 * @param constructorToUse 要使用的构造函数
	 * @param argsToUse constructorToUse所用到的参数值
	 * @return 使用constructorToUse生成出来的与beanName对应的Bean对象
	 */
	private Object instantiate(
			String beanName, RootBeanDefinition mbd, Constructor<?> constructorToUse, Object[] argsToUse) {

		try {
			//获取Bean工厂用于创建Bean实例的实例化策略
			InstantiationStrategy strategy = this.beanFactory.getInstantiationStrategy();
			//如果有安全管理其
			if (System.getSecurityManager() != null) {
				//以特权方式执行：让strategy使用ctor创建beanName所对应的Bean对象
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse),
						this.beanFactory.getAccessControlContext());
			}
			else {
				//让strategy使用ctor创建beanName所对应的Bean对象
				return strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
			}
		}
		//捕捉所有实例化对象过程中的异常
		catch (Throwable ex) {
			//抛出BeanCreationException:通过工厂方法实例化Bean失败
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via constructor failed", ex);
		}
	}

	/**
	 * <p>如果可能，解析指定的beanDefinition中factory方法.:
	 *  <ol>
	 *   <li>定义用于引用工厂类对象的类对象【变量 factoryClass】</li>
	 *   <li>定义是否是静态标记【变量 isStatic】</li>
	 *   <li>如果mbd的FactoryBean名不为null:
	 *    <ol>
	 *     <li>使用beanFactory确定mbd的FactoryBean名的bean类型，将结果赋值给factoryClass。为了
	 *     确定其对象类型，默认让FactoryBean以初始化</li>
	 *     <li>isStatic设置为false，表示不是静态方法</li>
	 *    </ol>
	 *   </li>
	 *   <li>否则：获取mbd包装好的Bean类赋值给factoryClass;将isStatic设置为true，表示是静态方法</li>
	 *   <li>如果factoryClass为null,抛出异常：无法解析工厂类</li>
	 *   <li>如果clazz是CGLIB生成的子类，则返回该子类的父类，否则直接返回要检查的类。将结果重新赋
	 *   值给factoryClass</li>
	 *   <li>根据mbd是否允许访问非公共构造函数和方法来获取factoryClass的所有候选方法【变量 candidates】</li>
	 *   <li>定义用于存储唯一方法对象的Method对象【变量 uniqueCandidate】</li>
	 *   <li>遍历candidates,元素为candidate:
	 *    <ol>
	 *     <li>如果candidate的静态标记与静态标记相同 且 candidate有资格作为工厂方法:
	 *      <ol>
	 *       <li>如果uniqueCandidate还没有引用,将uniqueCandidate引用该candidate</li>
	 *       <li>如果uniqueCandidate的参数类型数组与candidate的参数类型数组不一致,则取消uniqueCandidate的引用,
	 *       并跳出循环</li>
	 *      </ol>
	 *     </li>
	 *    </ol>
	 *   </li>
	 *   <li>将mbd用于自省的唯一工厂方法候选的缓存引用【{@link RootBeanDefinition#factoryMethodToIntrospect}】上uniqueCandidate</li>
	 *  </ol>
	 * </p>
	 * Resolve the factory method in the specified bean definition, if possible.
	 * {@link RootBeanDefinition#getResolvedFactoryMethod()} can be checked for the result.
	 * <p>如果可能，指定的beanDefinition中解析factory方法.
	 * 可以检查RootBeanDefinition.getResolvedFactoryMethod()的结果</p>
	 * @param mbd the bean definition to check -- 要检查的bean定义
	 */
	public void resolveFactoryMethodIfPossible(RootBeanDefinition mbd) {
		//定义用于引用工厂类对象的类对象
		Class<?> factoryClass;
		//定义是否是静态标记
		boolean isStatic;
		//如果mbd的FactoryBean名不为null
		if (mbd.getFactoryBeanName() != null) {
			//使用beanFactory确定mbd的FactoryBean名的bean类型。为了确定其对象类型，默认让FactoryBean以初始化
			factoryClass = this.beanFactory.getType(mbd.getFactoryBeanName());
			//静态标记设置为false，表示不是静态方法
			isStatic = false;
		}
		else {
			//获取mbd包装好的Bean类
			factoryClass = mbd.getBeanClass();
			//静态标记设置为true，表示是静态方法
			isStatic = true;
		}
		//如果factoryClass为null,抛出异常：无法解析工厂类
		Assert.state(factoryClass != null, "Unresolvable factory class");
		//如果clazz是CGLIB生成的子类，则返回该子类的父类，否则直接返回要检查的类
		factoryClass = ClassUtils.getUserClass(factoryClass);
		//根据mbd的是否允许访问非公共构造函数和方法标记【RootBeanDefinition.isNonPublicAccessAllowed】来获取factoryClass的所有候选方法
		Method[] candidates = getCandidateMethods(factoryClass, mbd);
		//定义用于存储唯一方法对象的Method对象
		Method uniqueCandidate = null;
		//遍历candidates
		for (Method candidate : candidates) {
			//如果candidate的静态标记与静态标记相同 且 candidate有资格作为工厂方法
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
				//如果uniqueCandidate还没有引用
				if (uniqueCandidate == null) {
					//将uniqueCandidate引用该candidate
					uniqueCandidate = candidate;
				}
				//如果uniqueCandidate的参数类型数组与candidate的参数类型数组不一致
				else if (!Arrays.equals(uniqueCandidate.getParameterTypes(), candidate.getParameterTypes())) {
					//取消uniqueCandidate的引用
					uniqueCandidate = null;
					//跳出循环
					break;
				}
			}
		}
		//将mbd用于自省的唯一工厂方法候选的缓存引用上uniqueCandidate
		mbd.factoryMethodToIntrospect = uniqueCandidate;
	}

	/**
	 * <p>根据mbd的是否允许访问非公共构造函数和方法标记【{@link RootBeanDefinition#isNonPublicAccessAllowed}】来获取factoryClass的所有候选方法。</p>
	 * Retrieve all candidate methods for the given class, considering
	 * the {@link RootBeanDefinition#isNonPublicAccessAllowed()} flag.
	 * Called as the starting point for factory method determination.
	 * <p>考虑RootBeanDefinition.isNoPubilcAccessAllowed()标志,检查给定类的所有候选
	 * 方法.称为确定工厂方法的起点。</p>
	 */
	private Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
		//如果有系统安全管理器
		if (System.getSecurityManager() != null) {
			//使用特权方式执行:如果mbd允许访问非公共构造函数和方法，就返回factoryClass子类和其父类的所有声明方法，首先包括子类方法；
			// 否则只获取factoryClass的public级别方法
			return AccessController.doPrivileged((PrivilegedAction<Method[]>) () ->
					(mbd.isNonPublicAccessAllowed() ?
						ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods()));
		}
		else {
			//如果mbd允许访问非公共构造函数和方法，就返回factoryClass子类和其父类的所有声明方法，首先包括子类方法；
			//	否则只获取factoryClass的public级别方法
			return (mbd.isNonPublicAccessAllowed() ?
					ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods());
		}
	}

	/**
	 * <p>使用工厂方法实例化beanName所对应的Bean对象:
	 *  <ol>
	 *   <li>新建一个BeanWrapperImp实例，用于封装使用工厂方法生成与beanName对应的Bean对象【变量 bw】</li>
	 *   <li>初始化bw</li>
	 *   <li>【<b>1.获取工厂Bean对象，工厂Bean对象的类对象，确定工厂方法是否是静态</b>】:
	 *    <ol>
	 *     <li>定义一个用于存放工厂Bean对象的Object【变量 factoryBean】</li>
	 *     <li>定义一个用于存放工厂Bean对象的类对象的Class【变量 factoryClass】</li>
	 *     <li>定义一个表示是静态工厂方法的标记【变量 isStatic】</li>
	 *     <li>从mbd中获取配置的FactoryBean名【变量 factoryBeanName】</li>
	 *     <li>如果factoryBeanName不为null：
	 *      <ol>
	 *       <li>如果factoryBean名与beanName相同,抛出BeanDefinitionStoreException</li>
	 *       <li>从bean工厂中获取factoryBeanName所指的factoryBean对象</li>
	 *       <li>如果mbd配置为单例作用域 且 beanName已经在bean工厂的单例对象的高速缓存Map中,抛出 ImplicitlyAppearedSingletonException</li>
	 *       <li>获取factoryBean的Class对象【变量 factoryClass】</li>
	 *       <i>设置isStatic为false,表示不是静态方法</li>
	 *      </ol>
	 *     </li>
	 *     <li>否则：
	 *      <ol>
	 *       <li>如果mbd没有指定bean类,抛出 BeanDefinitionStoreException</li>
	 *       <li>将factoryBean设为null</li>
	 *       <li>指定factoryClass引用mbd指定的bean类</li>
	 *       <li>设置isStatic为true，表示是静态方法</li>
	 *      </ol>
	 *     </li>
	 *    </ol>
	 *   </li>
	 *   <li>【<b>2. 尝试从mbd的缓存属性中获取要使用的工厂方法，要使用的参数值数组</b>】:
	 *    <ol>
	 *     <li>声明一个要使用的工厂方法，默认为null【变量 factoryMethodToUse】</li>
	 *     <li>声明一个参数持有人对象，默认为null 【变量 argsHolderToUse】</li>
	 *     <li>声明一个要使用的参数值数组,默认为null 【变量 argsToUse】</li>
	 *     <li>如果explicitArgs不为null,argsToUse就引用explicitArgs</li>
	 *     <li>否则：
	 *      <ol>
	 *       <li>声明一个要解析的参数值数组，默认为null 【变量 argsToResolve】</li>
	 *       <li>使用mbd的构造函数字段通用锁【{@link RootBeanDefinition#constructorArgumentLock}】进行加锁，以保证线程安全:
	 *        <ol>
	 *         <li>指定factoryMethodToUser引用mbd已解析的构造函数或工厂方法对象</li>
	 *         <li>如果factoryMethodToUser不为null 且 mbd已解析构造函数参数:
	 *          <ol>
	 *           <li>指定argsToUser引用mbd完全解析的构造函数参数值</li>
	 *           <li>如果argsToUse为null,指定argsToResolve引用mbd部分准备好的构造函数参数值</li>
	 *          </ol>
	 *         </li>
	 *        </ol>
	 *       </li>
	 *       <li>如果argsToResolve不为null,即表示mbd还没有完全解析的构造函数参数值,就解析缓存在mbd中准备好的参数值,
	 *       允许在没有此类BeanDefintion的时候回退</li>
	 *      </ol>
	 *     </li>
	 *    </ol>
	 *   </li>
	 *   <li>【<b>3. 在没法从mbd的缓存属性中获取要使用的工厂方法，要使用的参数值数组时，尝试从候选工厂方法中获取要使用的工厂方法以及要使用的参数值数组:</b>】
	 *    <ol>
	 *     <li>如果factoryMethoToUse为null或者argsToUser为null:
	 *      <ol>
	 *       <li><b>3.1 获取工厂类的所有候选工厂方法</b>:
	 *        <ol>
	 *         <li>让factoryClass重新引用经过解决CGLIB问题所得到Class对象</li>
	 *         <li>定义一个用于存储候选方法的集合【变量 candidateList】</li>
	 *         <li>如果mbd所配置工厂方法时唯一:
	 *          <ol>
	 *           <li>如果factoryMethodToUse为null,获取mbd解析后的工厂方法对象</li>
	 *           <li>如果factoryMethodToUse不为null,就新建一个不可变，只能存一个对象的集合，将factoryMethodToUse添加进行，然后
	 *           让candidateList引用该集合</li>
	 *          </ol>
	 *         </li>
	 *         <li>如果candidateList为null:
	 *          <ol>
	 *           <li>让candidateList引用一个新的ArrayList</li>
	 *           <li>根据mbd的是否允许访问非公共构造函数和方法标记【{@link RootBeanDefinition#isNonPublicAccessAllowed}】来获取
	 *           factoryClass的所有候选方法</li>
	 *           <li>遍历rawCandidates,元素名为candidate:如果candidate的修饰符与isStatic一致且candidate有资格作为mdb的
	 *           工厂方法,将candidate添加到candidateList中</li>
	 *          </ol>
	 *         </li>
	 *        </ol>
	 *       </li>
	 *       <li>【<b>3.2 候选方法只有一个且没有构造函数时，就直接使用该候选方法生成与beanName对应的Bean对象封装到bw中返回出去</b>】:
	 *        <ol>
	 *         <li>如果candidateList只有一个元素 且 没有传入构造函数值 且 mbd也没有构造函数参数值:
	 *          <ol>
	 *           <li>获取candidateList中唯一的方法 【变量 uniqueCandidate】</li>
	 *           <li>如果uniqueCandidate是不需要参数:
	 *            <ol>
	 *             <li>让mbd缓存uniqueCandidate【{@link RootBeanDefinition#factoryMethodToIntrospect}】</li>
	 *             <li>使用mdb的构造函数字段的通用锁【{@link RootBeanDefinition#constructorArgumentLock}】进行加锁以保证
	 *             线程安全:
	 *              <ol>
	 *               <li>让mbd缓存已解析的构造函数或工厂方法【{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}】</li>
	 *               <li>让mbd标记构造函数参数已解析【{@link RootBeanDefinition#constructorArgumentsResolved}】</li>
	 *               <li>让mbd缓存完全解析的构造函数参数【{@link RootBeanDefinition#resolvedConstructorArguments}】</li>
	 *              </ol>
	 *             </li>
	 *             <li>使用factoryBean生成的与beanName对应的Bean对象,并将该Bean对象保存到bw中</li>
	 *             <li>将bw返回出去</li>
	 *            </ol>
	 *           </li>
	 *          </ol>
	 *         </li>
	 *        </ol>
	 *       </li>
	 *       <li><b>3.3 筛选出最匹配的候选工厂方法，以及解析出去对应的工厂方法的参数值</b>:
	 *        <ol>
	 *         <li>将candidateList转换成数组【变量 candidates】</li>
	 *         <li>对candidates进行排序，首选公共方法和带有最多参数的'greedy'方法</li>
	 *         <li>定义一个用于存放解析后的构造函数参数值的ConstructorArgumentValues对象【变量 resolvedValues】</li>
	 *         <li>定义一个mbd是否支持使用构造函数进行自动注入的标记【变量 autowiring】</li>
	 *         <li>定义一个最小类型差异权重，默认是Integer最大值【变量 minTypeDiffWeight】</li>
	 *         <li>定义一个存储摸棱两可的工厂方法的Set集合,以用于抛出BeanCreationException时描述异常信息【变量 ambiguousFactoryMethods】</li>
	 *         <li>定义一个最少参数数，默认为0【变量 minNrOfArgs】</li>
	 *         <li>如果explicitArgs不为null,minNrOfArgs引用explitArgs的数组长度</li>
	 *         <li>否则:
	 *          <ol>
	 *           <li>如果mbd有构造函数参数值:
	 *            <ol>
	 *             <li>获取mbd的构造函数参数值Holder【变量 cargs】</li>
	 *             <li>对resolvedValues实例化为ConstructorArgumentValues对象</li>
	 *             <li>将cargs解析后值保存到resolveValues中，并让minNrOfArgs引用解析后的最小(索引参数值数+泛型参数值数)</li>
	 *            </ol>
	 *           </li>
	 *           <li>否则：意味着mbd没有构造函数参数值时，将minNrOfArgs设为0</li>
	 *          </ol>
	 *         </li>
	 *         <li>定义一个用于保存UnsatisfiedDependencyException的列表【变量 causes】</li>
	 *         <li>遍历candidate，元素名为candidate：
	 *          <ol>
	 *           <li>获取candidated的参数类型数组【变量 paramTypes】</li>
	 *           <li>如果paramTypes的数组长度大于等于minNrOfArgs:
	 *            <ol>
	 *             <li>定义一个封装参数数组的ArgumentsHolder对象【变量 argsHolder】</li>
	 *             <li>如果explicitArgs不为null:
	 *              <ol>
	 *               <li>如果paramTypes的长度与explicitArgsd额长度不相等,跳过当次循环中剩下的步骤，
	 *               执行下一次循环。</li>
	 *               <li>实例化argsHolder，封装explicitArgs到argsHolder</li>
	 *              </ol>
	 *             </li>
	 *             <li>否则：
	 *              <ol>
	 *               <li>定义用于保存参数名的数组【变量 paramNames】</li>
	 *               <li>获取beanFactory的参数名发现器【变量 pnd】</li>
	 *               <li>如果pnd不为null,通过pnd解析candidate的参数名</li>
	 *               <li>将resolvedValues转换成一个封装着参数数组ArgumentsHolder实例，当candidate只有一个时，支持可在抛
	 *               出没有此类BeanDefintion的异常返回null，而不抛出异常</li>
	 *               <li>捕捉解析参数值时出现的UnsatisfiedDependencyException:
	 *                <ol>
	 *                 <li>如果当前日志可打印跟踪级别的信息,打印跟踪级别日志</li>
	 *                 <li>如果cause为null,对cause进行实例化成LinkedList对象</li>
	 *                 <li>将ex添加到causes中</li>
	 *                 <li>跳过本次循环体中余下尚未执行的语句，立即进行下一次的循环</li>
	 *                </ol>
	 *               </li>
	 *              </ol>
	 *             </li>
	 *             <li>如果bd支持的构造函数解析模式时宽松模式,引用获取类型差异权重值，否则引用获取Assignabliity权重值【变量 typeDiffWeight】</li>
	 *             <li>如果typeDiffWeight小于minTypeDiffWeight:
	 *              <ol>
	 *               <li>让factoryMethodToUser引用candidate</li>
	 *               <li>让argsHolderToUse引用argsHolder</li>
	 *               <li>让argToUse引用argsHolder的经过转换后参数值数组</li>
	 *               <li>让minTypeDiffWeight引用typeDiffWeight</li>
	 *               <li>将ambiguousFactoryMethods置为null</li>
	 *              </ol>
	 *             </li>
	 *             <li>【else if】如果factoryMethodToUse不为null 且 typeDiffWeight与minTypeDiffWeight相等且mbd指定了严格模式解析构造函
	 *             数且paramTypes的数组长度与factoryMethodToUse的参数数组长度相等且paramTypes的数组元素与factoryMethodToUse的参
	 *             数数组元素不相等:
	 *              <ol>
	 *               <li>如果ambiguousFactoryMethods为null:
	 *                <ol>
	 *                 <li>初始化ambiguousFactoryMethods为LinkedHashSet实例</li>
	 *                 <li>将factoryMethodToUse添加到ambiguousFactoryMethods中</li>
	 *                </ol>
	 *               </li>
	 *               <li>将candidate添加到ambiguousFactoryMethods中</li>
	 *              </ol>
	 *             </li>
	 *            </ol>
	 *           </li>
	 *          </ol>
	 *         </li>
	 *        </ol>
	 *       </li>
	 *       <li>【<b>3.4 整合无法筛选出候选方法 或者 无法解析出要使用的参数值的情况，抛出BeanCreationException并加以描述</b>】:
	 *        <ol>
	 *         <li>如果factoryMethodToUse为null或者argsToUse为null:
	 *          <ol>
	 *           <li>如果causes不为null,从cause中移除最新的UnsatisfiedDependencyException</li>
	 *           <li>遍历causes,元素为cause:将cause添加到该Bean工厂的抑制异常列表【{@link DefaultSingletonBeanRegistry#suppressedExceptions】中</li>
	 *           <li>定义一个用于存放参数类型的简单类名的ArrayList对象，长度为minNrOfArgs</li>
	 *           <li>如果explicitArgs不为null:遍历explicitArgs.元素为arg:如果arg不为null，将arg的简单类名添加到argTypes中；否则将"null"添加到argTyps中</li>
	 *           <li>如果resolvedValues不为null:
	 *            <ol>
	 *             <li>定义一个用于存放resolvedValues的泛型参数值和方法参数值的LinkedHashSet对象 【变量 valueHolders】</li>
	 *             <li>将resolvedValues的方法参数值添加到valueHolders中</li>
	 *             <li>将resolvedValues的泛型参数值添加到valueHolders中</li>
	 *             <li>遍历valueHolders，元素为value:
	 *              <ol>
	 *               <li>如果value的参数类型不为null，就获取该参数类型的简单类名；否则(如果value的参数值不为null，即获取该参数值的简单类名;否则为"null")</li>
	 *               <li>将argType添加到argTypes中</li>
	 *              </ol>
	 *             </li>
	 *            </ol>
	 *           </li>
	 *           <li>将argType转换成字符串，以","隔开元素.用于描述Bean创建异常</li>
	 *           <li>抛出BeanCreationException</li>
	 *          </ol>
	 *         </li>
	 *         <li>如果factoryMethodToUse时无返回值方法,抛出BeanCreationException</li>
	 *         <li>如果ambiguousFactoryMethods不为null,抛出BeanCreationException</li>
	 *        </ol>
	 *       </li>
	 *       <li>【<b>3.5 将筛选出来的工厂方法和解析出来的参数值缓存到mdb中</b>】:
	 *        <ol>
	 *         <li>如果explicitArgs为null 且 argsHolderToUser不为null:
	 *          <ol>
	 *           <li>让mbd的唯一方法候选【{@link RootBeanDefinition#factoryMethodToIntrospect}】引用factoryMethodToUse</li>
	 *           <li>将argsHolderToUse所得到的参数值属性缓存到mbd对应的属性中</li>
	 *          </ol>
	 *         </li>
	 *        </ol>
	 *       </li>
	 *      </ol>
	 *     </li>
	 *    </ol>
	 *   </li>
	 *   <li>【<b>4. 使用factoryBean生成与beanName对应的Bean对象,并将该Bean对象保存到bw中</b>】</li>
	 *   <li>将bw返回出去</li>
	 *  </ol>
	 * </p>
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * bean definition parameter specifies a class, rather than a "factory-bean", or
	 * an instance variable on a factory object itself configured using Dependency Injection.
	 * <p>使用命名工厂方法实例化bean。如果beanDefinition参数指定一个类，而不是"factory-bean"，或者
	 * 使用依赖注入配置的工厂对象本身的实例，则该方法可以是静态的。</p>
	 * <p>Implementation requires iterating over the static or instance methods with the
	 * name specified in the RootBeanDefinition (the method may be overloaded) and trying
	 * to match with the parameters. We don't have the types attached to constructor args,
	 * so trial and error is the only way to go here. The explicitArgs array may contain
	 * argument values passed in programmatically via the corresponding getBean method.
	 * <p>实现需要使用RootBeanDefinition中指定的名称迭代静态方法或实例方法(该方法可能会重载),
	 * 并尝试与参数匹配。我们没有将类型附加到构造函数args上,因此反复试验是此处的唯一方法。explicitArgs
	 * 数组可以包含通过相应的getBean方法以编程方式传递的参数值。</p>
	 * @param beanName the name of the bean -- bean名
	 * @param mbd the merged bean definition for the bean -- beanName对于的合并后RootBeanDefinition
	 * @param explicitArgs argument values passed in programmatically via the getBean
	 * method, or {@code null} if none (-> use constructor argument values from bean definition)
	 * -- 通过getBean方法以编程方式传递的参数值；如果没有，则返回null(->使用bean定义的构造函数参数值)
	 * @return a BeanWrapper for the new instance -- 新实例的BeanWrapper
	 */
	public BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {
		//新建一个BeanWrapperImp实例，用于封装使用工厂方法生成与beanName对应的Bean对象
		BeanWrapperImpl bw = new BeanWrapperImpl();
		//初始化bw
		this.beanFactory.initBeanWrapper(bw);

		//1.获取工厂Bean对象，工厂Bean对象的类对象，确定工厂方法是否是静态
		//定义一个用于存放工厂Bean对象的Object
		Object factoryBean;
		//定义一个用于存放工厂Bean对象的类对象的Class
		Class<?> factoryClass;
		//定义一个表示是静态工厂方法的标记
		boolean isStatic;

		//从mbd中获取配置的FactoryBean名
		String factoryBeanName = mbd.getFactoryBeanName();
		//如果factoryBeanName不为null
		if (factoryBeanName != null) {
			//如果factoryBean名与beanName相同
			if (factoryBeanName.equals(beanName)) {
				//抛出 BeanDefinitionStoreException：factory-bean引用指向相同的beanDefinition
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}
			//从bean工厂中获取factoryBeanName所指的factoryBean对象
			factoryBean = this.beanFactory.getBean(factoryBeanName);
			//如果mbd配置为单例作用域 且 beanName已经在bean工厂的单例对象的高速缓存Map中
			if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
				//这个时候意味着Bean工厂中已经有beanName的Bean对象，而这个还要生成多一个Bean名为BeanName的Bean对象，导致了冲突
				//抛出ImplicitlyAppearedSingletonException，
				throw new ImplicitlyAppearedSingletonException();
			}
			//获取factoryBean的Class对象
			factoryClass = factoryBean.getClass();
			//设置isStatic为false,表示不是静态方法
			isStatic = false;
		}
		else {
			// It's a static factory method on the bean class.
			// 这是bean类上的静态工厂方法
			//如果mbd没有指定bean类
			if (!mbd.hasBeanClass()) {
				//抛出 BeanDefinitionStoreException：beanDefinition即不声明bean类，又不声明FactoryBean引用
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"bean definition declares neither a bean class nor a factory-bean reference");
			}
			//将factoryBean设为null
			factoryBean = null;
			//指定factoryClass引用mbd指定的bean类
			factoryClass = mbd.getBeanClass();
			//设置isStatic为true，表示是静态方法
			isStatic = true;
		}

		//2. 尝试从mbd的缓存属性中获取要使用的工厂方法，要使用的参数值数组
		//声明一个要使用的工厂方法，默认为null
		Method factoryMethodToUse = null;
		//声明一个用于存储不同形式的参数值的ArgumentsHolder，默认为null
		ArgumentsHolder argsHolderToUse = null;
		//声明一个要使用的参数值数组,默认为null
		Object[] argsToUse = null;

		//如果explicitArgs不为null
		if (explicitArgs != null) {
			//argsToUse就引用explicitArgs
			argsToUse = explicitArgs;
		}
		else {//如果没有传
			//声明一个要解析的参数值数组，默认为null
			Object[] argsToResolve = null;
			//使用mbd的构造函数字段通用锁进行加锁，以保证线程安全
			synchronized (mbd.constructorArgumentLock) {
				//指定factoryMethodToUser引用mbd已解析的构造函数或工厂方法对象
				factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;
				//如果factoryMethodToUser不为null 且 mbd已解析构造函数参数
				if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached factory method...
					//找到了缓存的工厂方法
					//指定argsToUser引用mbd完全解析的构造函数参数值
					argsToUse = mbd.resolvedConstructorArguments;
					//如果argsToUse为null
					if (argsToUse == null) {
						//指定argsToResolve引用mbd部分准备好的构造函数参数值
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			//如果argsToResolve不为null,即表示mbd还没有完全解析的构造函数参数值
			if (argsToResolve != null) {
				//解析缓存在mbd中准备好的参数值,允许在没有此类BeanDefintion的时候回退
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve, true);
			}
		}

		//3. 在没法从mbd的缓存属性中获取要使用的工厂方法，要使用的参数值数组时，尝试从候选工厂方法中获取要使用的工厂方法以及要使用的参数值数组
		//如果factoryMethoToUse为null或者argsToUser为null
		if (factoryMethodToUse == null || argsToUse == null) {
			// 3.1 获取工厂类的所有候选工厂方法
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.
			//需要确定工厂方法...
			//尝试使用此名称的所有方法，以查看它们是否与给定参数匹配
			// ClassUtils.getUserClass：如果clazz是CGLIB生成的子类，则返回该子类的父类，否则直接返回要检查的类
			//让factoryClass重新引用经过解决CGLIB问题所得到Class对象
			factoryClass = ClassUtils.getUserClass(factoryClass);
			//定义一个用于存储候选方法的集合
			List<Method> candidateList = null;
			//如果mbd所配置工厂方法时唯一
			if (mbd.isFactoryMethodUnique) {
				//如果factoryMethodToUse为null
				if (factoryMethodToUse == null) {
					//获取mbd解析后的工厂方法对象
					factoryMethodToUse = mbd.getResolvedFactoryMethod();
				}
				//如果factoryMethodToUse不为null
				if (factoryMethodToUse != null) {
					// Collections.singletonList()返回的是不可变的集合，但是这个长度的集合只有1，可以减少内存空间。但是返回的值依然是Collections的内部实现类，
					// 同样没有add的方法，调用add，set方法会报错
					// 新建一个不可变，只能存一个对象的集合，将factoryMethodToUse添加进行，然后让candidateList引用该集合
					candidateList = Collections.singletonList(factoryMethodToUse);
				}
			}
			//如果candidateList为null
			if (candidateList == null) {
				//让candidateList引用一个新的ArrayList
				candidateList = new ArrayList<>();
				//根据mbd的是否允许访问非公共构造函数和方法标记【RootBeanDefinition.isNonPublicAccessAllowed】来获取factoryClass的所有候选方法
				Method[] rawCandidates = getCandidateMethods(factoryClass, mbd);
				//遍历rawCandidates,元素名为candidate
				for (Method candidate : rawCandidates) {
					//如果candidate的修饰符与isStatic一致 且 candidate有资格作为mdb的工厂方法
					if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
						//将candidate添加到candidateList中
						candidateList.add(candidate);
					}
				}
			}
			//3.2 候选方法只有一个且没有构造函数时，就直接使用该候选方法生成与beanName对应的Bean对象封装到bw中返回出去
			//如果candidateList只有一个元素 且 没有传入构造函数值 且 mbd也没有构造函数参数值
			if (candidateList.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				//获取candidateList中唯一的方法
				Method uniqueCandidate = candidateList.get(0);
				//如果uniqueCandidate是不需要参数
				if (uniqueCandidate.getParameterCount() == 0) {
					//让mbd缓存uniqueCandidate【{@link RootBeanDefinition#factoryMethodToIntrospect}】
					mbd.factoryMethodToIntrospect = uniqueCandidate;
					//使用mdb的构造函数字段的通用锁【{@link RootBeanDefinition#constructorArgumentLock}】进行加锁以保证线程安全
					synchronized (mbd.constructorArgumentLock) {
						//让mbd缓存已解析的构造函数或工厂方法【{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}】
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						//让mbd标记构造函数参数已解析【{@link RootBeanDefinition#constructorArgumentsResolved}】
						mbd.constructorArgumentsResolved = true;
						//让mbd缓存完全解析的构造函数参数【{@link RootBeanDefinition#resolvedConstructorArguments}】
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					//使用factoryBean生成的与beanName对应的Bean对象,并将该Bean对象保存到bw中
					bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, uniqueCandidate, EMPTY_ARGS));
					//将bw返回出去
					return bw;
				}
			}

			//3.3 筛选出最匹配的候选工厂方法，以及解析出去对应的工厂方法的参数值
			//将candidateList转换成数组
			Method[] candidates = candidateList.toArray(new Method[0]);
			//对candidates进行排序，首选公共方法和带有最多参数的'greedy'方法
			AutowireUtils.sortFactoryMethods(candidates);
 			//ConstructorArgumentValues：构造函数参数值的Holder,通常作为BeanDefinition的一部分,支持构造函数参数列表中特定索引的值
			// 以及按类型的通用参数匹配
			//定义一个用于存放解析后的构造函数参数值的ConstructorArgumentValues对象
			ConstructorArgumentValues resolvedValues = null;
			//定义一个mbd是否支持使用构造函数进行自动注入的标记
			boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			//定义一个最小类型差异权重，默认是Integer最大值
			int minTypeDiffWeight = Integer.MAX_VALUE;
			//定义一个存储摸棱两可的工厂方法的Set集合,以用于抛出BeanCreationException时描述异常信息
			Set<Method> ambiguousFactoryMethods = null;

			//定义一个最少参数数，默认为0
			int minNrOfArgs;
			//如果explicitArgs不为null
			if (explicitArgs != null) {
				//minNrOfArgs引用explitArgs的数组长度
				minNrOfArgs = explicitArgs.length;
			}
			else {
				// We don't have arguments passed in programmatically, so we need to resolve the
				// arguments specified in the constructor arguments held in the bean definition.
				// 我们没有以编程方式传递参数，因此我们需要解析BeanDefinition中保存的构造函数参数中指定的参数
				//如果mbd有构造函数参数值
				if (mbd.hasConstructorArgumentValues()) {
					//获取mbd的构造函数参数值Holder
					ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
					//对resolvedValues实例化
					resolvedValues = new ConstructorArgumentValues();
					//将cargs解析后值保存到resolveValues中，并让minNrOfArgs引用解析后的最小(索引参数值数+泛型参数值数)
					minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
				}
				else {
					//意味着mbd没有构造函数参数值时，将minNrOfArgs设为0
					minNrOfArgs = 0;
				}
			}

			//定义一个用于UnsatisfiedDependencyException的列表
			LinkedList<UnsatisfiedDependencyException> causes = null;

			//遍历candidates，元素名为candidate
			for (Method candidate : candidates) {
				//获取candidated的参数类型数组
				Class<?>[] paramTypes = candidate.getParameterTypes();

				//如果paramTypes的数组长度大于等于minNrOfArgs
				if (paramTypes.length >= minNrOfArgs) {
					//ArgumentsHolder:用于保存参数数组
					//定义一个封装参数数组的ArgumentsHolder对象
					ArgumentsHolder argsHolder;
					//如果explicitArgs不为null
					if (explicitArgs != null) {
						// Explicit arguments given -> arguments length must match exactly.
						// 给定的显示参数->参数长度必须完全匹配
						//如果paramTypes的长度与explicitArgsd额长度不相等
						if (paramTypes.length != explicitArgs.length) {
							//跳过当次循环中剩下的步骤，执行下一次循环。
							continue;
						}
						//实例化argsHolder，封装explicitArgs到argsHolder
						argsHolder = new ArgumentsHolder(explicitArgs);
					}
					else {
						// Resolved constructor arguments: type conversion and/or autowiring necessary.
						// 已解析的构造函数参数:类型转换 and/or 自动注入时必须的
						try {
							//定义用于保存参数名的数组
							String[] paramNames = null;
							//获取beanFactory的参数名发现器
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							//如果pnd不为null
							if (pnd != null) {
								//通过pnd解析candidate的参数名
								paramNames = pnd.getParameterNames(candidate);
							}
							//将resolvedValues转换成一个封装着参数数组ArgumentsHolder实例，当candidate只有一个时，支持可在抛
							// 出没有此类BeanDefintion的异常返回null，而不抛出异常
							argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw,
									paramTypes, paramNames, candidate, autowiring, candidates.length == 1);
						}
						//捕捉UnsatisfiedDependencyException
						catch (UnsatisfiedDependencyException ex) {
							//如果当前日志可打印跟踪级别的信息
							if (logger.isTraceEnabled()) {
								//打印跟踪级别日志：忽略bean'beanName'的工厂方法[candidate]
								logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
							}
							// Swallow and try next overloaded factory method.
							// 吞下并尝试下一个重载的工厂方法
							//如果cause为null
							if (causes == null) {
								//对cause进行实例化成LinkedList对象
								causes = new LinkedList<>();
							}
							//将ex添加到causes中
							causes.add(ex);
							//跳过本次循环体中余下尚未执行的语句，立即进行下一次的循环
							continue;
						}
					}
					//mbd支持的构造函数解析模式,默认使用宽松模式:
					// 1. 严格模式如果摸棱两可的构造函数在转换参数时都匹配，则抛出异常
					// 2. 宽松模式将使用"最接近类型匹配"的构造函数
					//如果bd支持的构造函数解析模式时宽松模式,引用获取类型差异权重值，否则引用获取Assignabliity权重值
					int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
							argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
					// Choose this factory method if it represents the closest match.
					// 如果它表示最接近的匹配项，则选择此工厂方法
					// 如果typeDiffWeight小于minTypeDiffWeight
					if (typeDiffWeight < minTypeDiffWeight) {
						//让factoryMethodToUser引用candidate
						factoryMethodToUse = candidate;
						//让argsHolderToUse引用argsHolder
						argsHolderToUse = argsHolder;
						//让argToUse引用argsHolder的经过转换后参数值数组
						argsToUse = argsHolder.arguments;
						//让minTypeDiffWeight引用typeDiffWeight
						minTypeDiffWeight = typeDiffWeight;
						//将ambiguousFactoryMethods置为null
						ambiguousFactoryMethods = null;
					}
					// Find out about ambiguity: In case of the same type difference weight
					// for methods with the same number of parameters, collect such candidates
					// and eventually raise an ambiguity exception.
					// However, only perform that check in non-lenient constructor resolution mode,
					// and explicitly ignore overridden methods (with the same parameter signature).
					// 找出歧义:如果具有相同数量参数的方法具有相同的类型差异权重，则收集此类候选想并最终引发歧义异常。
					// 但是，仅在非宽松构造函数解析模式下执行该检查，并显示忽略的方法（具有相同的参数签名）
					// 如果factoryMethodToUse不为null 且 typeDiffWeight与minTypeDiffWeight相等
					// 	且 mbd指定了严格模式解析构造函数 且 paramTypes的数组长度与factoryMethodToUse的参数数组长度相等 且
					// paramTypes的数组元素与factoryMethodToUse的参数数组元素不相等
					else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
							!mbd.isLenientConstructorResolution() &&
							paramTypes.length == factoryMethodToUse.getParameterCount() &&
							!Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
						//如果ambiguousFactoryMethods为null
						if (ambiguousFactoryMethods == null) {
							//初始化ambiguousFactoryMethods为LinkedHashSet实例
							ambiguousFactoryMethods = new LinkedHashSet<>();
							//将factoryMethodToUse添加到ambiguousFactoryMethods中
							ambiguousFactoryMethods.add(factoryMethodToUse);
						}
						//将candidate添加到ambiguousFactoryMethods中
						ambiguousFactoryMethods.add(candidate);
					}
				}
			}
			// 3.4 整合无法筛选出候选方法 或者 无法解析出要使用的参数值的情况，抛出BeanCreationException并加以描述
			//如果factoryMethodToUse为null或者argsToUse为null
			if (factoryMethodToUse == null || argsToUse == null) {
				//如果causes不为null
				if (causes != null) {
					//从cause中移除最新的UnsatisfiedDependencyException
					UnsatisfiedDependencyException ex = causes.removeLast();
					//遍历causes,元素为cause
					for (Exception cause : causes) {
						//将cause添加到该Bean工厂的抑制异常列表【{@link DefaultSingletonBeanRegistry#suppressedExceptions】 中
						this.beanFactory.onSuppressedException(cause);
					}
					//重新抛出ex
					throw ex;
				}
				//定义一个用于存放参数类型的简单类名的ArrayList对象，长度为minNrOfArgs
				List<String> argTypes = new ArrayList<>(minNrOfArgs);
				//如果explicitArgs不为null
				if (explicitArgs != null) {
					//遍历explicitArgs.元素为arg
					for (Object arg : explicitArgs) {
						//如果arg不为null，将arg的简单类名添加到argTypes中；否则将"null"添加到argTyps中
						argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
					}
				}
				//如果resolvedValues不为null
				else if (resolvedValues != null) {
					//定义一个用于存放resolvedValues的泛型参数值和方法参数值的LinkedHashSet对象
					Set<ValueHolder> valueHolders = new LinkedHashSet<>(resolvedValues.getArgumentCount());
					//将resolvedValues的方法参数值添加到valueHolders中
					valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());
					//将resolvedValues的泛型参数值添加到valueHolders中
					valueHolders.addAll(resolvedValues.getGenericArgumentValues());
					//遍历valueHolders，元素为value
					for (ValueHolder value : valueHolders) {
						//如果value的参数类型不为null，就获取该参数类型的简单类名；否则(如果value的参数值不为null，即获取该参数值的简单类名;否则为"null")
						String argType = (value.getType() != null ? ClassUtils.getShortName(value.getType()) :
								(value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null"));
						//将argType添加到argTypes中
						argTypes.add(argType);
					}
				}
				//将argType转换成字符串，以","隔开元素.用于描述Bean创建异常
				String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);
				//抛出BeanCreationException:找不到匹配的工厂方法：工厂Bean'mbd.getFactoryBeanName()';工厂方法
				// 	'mbd.getFactoryMethodName()(argDesc)'.检查是否存在具体指定名称和参数的方法，并且
				//	该方法时静态/非静态的.
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"No matching factory method found: " +
						(mbd.getFactoryBeanName() != null ?
							"factory bean '" + mbd.getFactoryBeanName() + "'; " : "") +
						"factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. " +
						"Check that a method with the specified name " +
						(minNrOfArgs > 0 ? "and arguments " : "") +
						"exists and that it is " +
						(isStatic ? "static" : "non-static") + ".");
			}
			//如果factoryMethodToUse时无返回值方法
			else if (void.class == factoryMethodToUse.getReturnType()) {
				//抛出BeanCreationException：无效工厂方法'mbd.getFactoryMethodName'需要具有非空返回类型
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid factory method '" + mbd.getFactoryMethodName() +
						"': needs to have a non-void return type!");
			}
			//如果ambiguousFactoryMethods不为null
			else if (ambiguousFactoryMethods != null) {
				//抛出BeanCreationException：在bean'beanName'中找到的摸棱两可的工厂方法匹配项(提示:为简单参数指定索引/类型/
				// 名称参数以避免类型歧义)：ambiguousFactoryMethods
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous factory method matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousFactoryMethods);
			}

			//3.5 将筛选出来的工厂方法和解析出来的参数值缓存到mdb中
			//如果explicitArgs为null 且 argsHolderToUser不为null
			if (explicitArgs == null && argsHolderToUse != null) {
				//让mbd的唯一方法候选【{@link RootBeanDefinition#factoryMethodToIntrospect}】引用factoryMethodToUse
				mbd.factoryMethodToIntrospect = factoryMethodToUse;
				//将argsHolderToUse所得到的参数值属性缓存到mbd对应的属性中
				argsHolderToUse.storeCache(mbd, factoryMethodToUse);
			}
		}
		//4. 使用factoryBean生成与beanName对应的Bean对象,并将该Bean对象保存到bw中
		bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
		//将bw返回出去
		return bw;
	}

	/**
	 * 使用factoryBean生成与beanName对应的Bean对象:
	 * <ol>
	 *  <li>如果有安全管理器,使用特权方式运行：在beanFactory中返回beanName的Bean实例，并通过factoryMethod
	 *  创建它 【{@link SimpleInstantiationStrategy#instantiate(RootBeanDefinition, String, BeanFactory, Object, Method, Object...)}】</li>
	 *  <li>否则:在beanFactory中返回beanName的Bean实例，并通过factoryMethod创建它
	 *  【{@link SimpleInstantiationStrategy#instantiate(RootBeanDefinition, String, BeanFactory, Object, Method, Object...)}】</li>
	 *  <li>捕捉所有实例化对象过程中的异常,抛出BeanCreationException:通过工厂方法实例化Bean失败</li>
	 * </ol>
	 * @param beanName 要生成的Bean对象所对应的bean名
	 * @param mbd  beanName对于的合并后RootBeanDefinition
	 * @param factoryBean 生成的Bean对象的工厂Bean对象
	 * @param factoryMethod factoryBean的工厂方法
	 * @param args factoryMethod所用到的参数值
	 * @return 使用factoryBean生成出来的与beanName对应的Bean对象
	 */
	private Object instantiate(String beanName, RootBeanDefinition mbd,
			@Nullable Object factoryBean, Method factoryMethod, Object[] args) {

		try {
			//如果有安全管理器
			if (System.getSecurityManager() != null) {
				//使用特权方式运行：在beanFactory中返回beanName的Bean实例，并通过factoryMethod创建它
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						this.beanFactory.getInstantiationStrategy().instantiate(
								mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args),
						this.beanFactory.getAccessControlContext());
			}
			else {
				//在beanFactory中返回beanName的Bean实例，并通过factoryMethod创建它
				return this.beanFactory.getInstantiationStrategy().instantiate(
						mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);
			}
		}
		//捕捉所有实例化对象过程中的异常
		catch (Throwable ex) {
			//抛出BeanCreationException:通过工厂方法实例化Bean失败
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via factory method failed", ex);
		}
	}

	/**
	 * <p>将cargs解析后值保存到resolveValues中，并返回解析后的最小(索引参数值数+泛型参数值数)</p>
	 * Resolve the constructor arguments for this bean into the resolvedValues object.
	 * This may involve looking up other beans.
	 * <p>将此Bean构造函数参数解析为resolveValues对象。这可能涉及查找其他Bean</p>
	 * <p>This method is also used for handling invocations of static factory methods.
	 * <p>此方法还用于处理静态工厂方法的调用</p>
	 * <ol>
	 *  <li>获取Bean工厂的类型转换器【变量 customConverter】</li>
	 *  <li>定义一个TypeConverter对象，如果有customConverter，就引用customConverter;否则引用bw【变量 converter】</li>
	 *  <li>新建一个BeanDefinitionValueResolver对象【变量 valueResolver】</li>
	 *  <li>获取cargs的参数值数量和泛型参数值数量作为 最小(索引参数值数+泛型参数值数)【变量 minNrOfArgs】</li>
	 *  <li>【<b>解析索引参数值</b>】遍历cargs所封装的索引参数值的Map，元素为entry(key=参数值的参数索引,value=
	 *  ConstructorArgumentValues.ValueHolder对象):
	 *   <ol>
	 *    <li>获取参数值的参数索引【变量 index】</li>
	 *    <li>如果index小于0,抛出Bean创建异常</li>
	 *    <li>如果index大于minNrOfArgs,minNrOfArgs就为index+1</li>
	 *    <li>获取ConstructorArgumentValues.ValueHolder对象【变量 valueHolder】</li>
	 *    <li>如果valueHolder已经包含转换后的值,将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中</li>
	 *    <li>否则:
	 *     <ol>
	 *      <li>使用valueResolver解析出valueHolder实例的构造函数参数值所封装的对象【变量 resolvedValue】</li>
	 *      <li>使用valueHolder所封装的type,name属性以及解析出来的resovledValue构造出一个ConstructorArgumentValues.ValueHolder对象</li>
	 *      <li>将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中</li>
	 *      <li>将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中</li>
	 *     </ol>
	 *    </li>
	 *   </ol>
	 *  </li>
	 *  <li>【<b>解析泛型参数值</b>】遍历cargs的泛型参数值的列表,元素为ConstructorArgumentValues.ValueHolder对象【变量 valueHolder】:
	 *   <ol>
	 *    <li>如果valueHolder已经包含转换后的值,将index和valueHolder添加到resolvedValues的泛型参数值的列表中</li>
	 *    <li>否则:
	 *     <ol>
	 *       <li>使用valueResolver解析出valueHolder实例的构造函数参数值所封装的对象</li>
	 *       <li>将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中</li>
	 *       <li>将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中</li>
	 *     </ol>
	 *    </li>
	 *   </ol>
	 *  </li>
	 *  <li>将minNrOfArgs【最小(索引参数值数+泛型参数值数)】返回出去</li>
	 * </ol>
	 * @param beanName bean名
	 * @param mbd beanName对于的合并后RootBeanDefinition
	 * @param bw Bean实例的包装对象
	 * @param cargs mbd的构造函数参数值Holder
	 * @param resolvedValues 解析后的构造函数参数值Holder
	 * @return 最小(索引参数值数+泛型参数值数)
	 */
	private int resolveConstructorArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues) {

		//获取Bean工厂的类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		//定义一个TypeConverter对象，如果有customConverter，就引用customConverter;否则引用bw
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		//BeanDefinitionValueResolver:在bean工厂实现中使用Helper类，它将beanDefinition对象中包含的值解析为应用于
		// 目标bean实例的实际值
		//新建一个BeanDefinitionValueResolver对象
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);

		//ConstructorArgumentValues.getArgumentCount():返回此实例中保存的参数值的数量，同时计算索引参数值和泛型参数值
		//获取cargs的参数值数量和泛型参数值数量作为 最小(索引参数值数+泛型参数值数)
		int minNrOfArgs = cargs.getArgumentCount();

		//ConstructorArgumentValues.ValueHolder：构造函数参数值的Holder,带有可选的type属性，指示实际构造函数参数的目标类型
		//遍历cargs所封装的索引参数值的Map，元素为entry(key=参数值的参数索引,value=ConstructorArgumentValues.ValueHolder对象)
		for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cargs.getIndexedArgumentValues().entrySet()) {
			//获取参数值的参数索引
			int index = entry.getKey();
			//如果index小于0
			if (index < 0) {
				//抛出Bean创建异常:无效的构造函数参数索引
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid constructor argument index: " + index);
			}
			//如果index大于最小参数值数量
			if (index > minNrOfArgs) {
				//minNrOfArgs就为index+1
				minNrOfArgs = index + 1;
			}
			//获取ConstructorArgumentValues.ValueHolder对象
			ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();
			//如果valueHolder已经包含转换后的值
			if (valueHolder.isConverted()) {
				//将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addIndexedArgumentValue(index, valueHolder);
			}
			else {
				//使用valueResolver解析出valueHolder实例的构造函数参数值所封装的对象
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				//使用valueHolder所封装的type,name属性以及解析出来的resovledValue构造出一个ConstructorArgumentValues.ValueHolder对象
				ConstructorArgumentValues.ValueHolder resolvedValueHolder =
						new ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.getType(), valueHolder.getName());
				//将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中
				resolvedValueHolder.setSource(valueHolder);
				//将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder);
			}
		}

		//遍历cargs的泛型参数值的列表,元素为ConstructorArgumentValues.ValueHolder对象
		for (ConstructorArgumentValues.ValueHolder valueHolder : cargs.getGenericArgumentValues()) {
			//如果valueHolder已经包含转换后的值
			if (valueHolder.isConverted()) {
				//将index和valueHolder添加到resolvedValues的泛型参数值的列表中
				resolvedValues.addGenericArgumentValue(valueHolder);
			}
			else {
				//使用valueResolver解析出valueHolder实例的构造函数参数值所封装的对象
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				//使用valueHolder所封装的type,name属性以及解析出来的resovledValue构造出一个ConstructorArgumentValues.ValueHolder对象
				ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(
						resolvedValue, valueHolder.getType(), valueHolder.getName());
				//将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中
				resolvedValueHolder.setSource(valueHolder);
				//将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addGenericArgumentValue(resolvedValueHolder);
			}
		}
		//返回 最小(索引参数值数+泛型参数值数)
		return minNrOfArgs;
	}

	/**
	 * Create an array of arguments to invoke a constructor or factory method,
	 * given the resolved constructor argument values.
	 * <p>给定已解析的构造函数参数值，创建一个参数数组以调用构造函数或工厂方法</p>
	 * <ol>
	 *  <li>获取bean工厂的自定义的TypeConverter【变量 customConverter】</li>
	 *  <li>如果customeConverter不为null,converter就引用customeConverter，否则引用bw【变量 converter】</li>
	 *  <li>根据paramTypes的数组长度构建一个ArgumentsHolder实例,用于保存解析后的参数值【变量 args】</li>
	 *  <li>定义一个用于存储构造函数参数值Holder，以查找下一个任意泛型参数值时，忽略该集合的元素的HashSet,初始化长度为paramTypes的数组长度
	 *  【变量 usedValueHolders】</li>
	 *  <li>定义一个用于存储自动注入Bean名的LinkedHashSet【变量 autowiredBeanNames】</li>
	 *  <li>fori形式遍历paramType,索引为paramIndex:
	 *   <ol>
	 *    <li>获取paramTypes中第paramIndex个参数类型【变量 paramType】</li>
	 *    <li>如果paramNames不为null，就引用第paramIndex个参数名否则引用空字符串【变量 paramName】</li>
	 *    <li>定义一个用于存储与paramIndex对应的ConstructorArgumentValues.ValueHolder实例【变量 valueHolder】</li>
	 *    <li>如果resolvedValues不为null:
	 *     <ol>
	 *      <li>在resolvedValues中查找与paramIndex对应的参数值，或者按paramType一般匹配【变量 valueHolder】</li>
	 *      <li>如果valueHolder为null 且 (mbd不支持使用构造函数进行自动注入 或者 paramTypes数组长度与resolvedValues的
	 *      (索引参数值+泛型参数值)数量相等)</li>
	 *     </ol>
	 *    </li>
	 *    <li>如果valueHolder不为null:
	 *     <ol>
	 *      <li>将valueHolder添加到usedValueHolders中，以表示该valueHolder已经使用过，下次在resolvedValues中
	 *      获取下一个valueHolder时，不要返回同一个对象</li>
	 *      <li>从valueHolder中获取原始参数值【变量 originalValue】</li>
	 *      <li>定义一个用于存储转换后的参数值的Object对象【变量 convertedValue】</li>
	 *      <li>如果valueHolder已经包含转换后的值:
	 *       <ol>
	 *        <li>从valueHolder中获取转换后的参数值【变量 convertedValue】</li>
	 *        <li>将convertedValue保存到args的preparedArguments数组的paramIndex对应元素中</li>
	 *       </ol>
	 *      </li>
	 *      <li>否则:
	 *       <ol>
	 *        <li>将executable中paramIndex对应的参数封装成MethodParameter对象【变量 methodParam】</li>
	 *        <li>使用converter将originalValue转换为paramType类型</li>
	 *        <li>捕捉在转换类型时出现的类型不匹配异常,重新抛出不满足的依赖异常</li>
	 *        <li>获取valueHolder的源对象，一般是ValueHolder【变量 sourceHolder】</li>
	 *        <li>如果sourceHolder是ConstructorArgumentValues.ValueHolder实例:
	 *         <ol>
	 *          <li>将soureHolder转换为ConstructorArgumentValues.ValueHolder对象【变量 sourceValue】</li>
	 *          <li>将args的resolveNecessary该为true，表示args.preparedArguments需要解析</li>
	 *          <li>将sourceValue保存到args的preparedArguments数组的paramIndex对应元素中</li>
	 *         </ol>
	 *        </li>
	 *       </ol>
	 *      </li>
	 *      <li>将convertedValue保存到args的arguments数组的paramIndex对应元素中</li>
	 *      <li>将originalValue保存到args的rawArguments数组的paramIndex对应元素中</li>
	 *     </ol>
	 *    </li>
	 *    <li>否则:
	 *     <ol>
	 *      <li>将executable中paramIndex对应的参数封装成MethodParameter对象【变量 methodParam】</li>
	 *      <li>mbd不支持适用构造函数进行自动注入,抛出不满足依赖异常</li>
	 *      <li>解析应该自动装配的methodParam的Bean对象,使用autowiredBeanNames保存所找到的所有候选Bean对象【变量 autowiredArgument】</li>
	 *      <li>将autowiredArgument保存到args的rawArguments数组的paramIndex对应元素中</li>
	 *      <li>将autowiredArgument保存到args的arguments数组的paramIndex对应元素中</li>
	 *      <li>将autowiredArgumentMarker保存到args的arguments数组的paramIndex对应元素中</li>
	 *      <li>将args的resolveNecessary该为true，表示args.preparedArguments需要解析</li>
	 *      <li>捕捉解析应该自动装配的methodParam的Bean对象时出现的BeanException,重新抛出满足依赖异常，引用mbd的资源描述作为异常信息</li>
	 *     </ol>
	 *    </li>
	 *    <li>遍历 autowiredBeanNames，元素为autowiredBeanName:
	 *     <ol>
	 *      <li>注册beanName与dependentBeanNamed的依赖关系到beanFactory中</li>
	 *      <li>如果当前日志级别时debug,打印debug日志</li>
	 *     </ol>
	 *    </li>
	 *    <li>将args(保存着解析后的参数值的ArgumentsHolder对象)返回出去</li>
	 *   </ol>
	 *  </li>
	 * </ol>
	 * @param beanName bean名
	 * @param mbd beanName对于的合并后RootBeanDefinition
	 * @param resolvedValues 已经过解析的构造函数参数值Holder对象
	 * @param bw bean实例包装类
	 * @param paramTypes executable的参数类型数组
	 * @param paramNames  executable的参数名数组
	 * @param executable 候选方法
	 * @param autowiring mbd是否支持使用构造函数进行自动注入的标记
	 * @param fallback 是否可在抛出NoSuchBeanDefinitionException返回null，而不抛出异常
	 */
	private ArgumentsHolder createArgumentArray(
			String beanName, RootBeanDefinition mbd, @Nullable ConstructorArgumentValues resolvedValues,
			BeanWrapper bw, Class<?>[] paramTypes, @Nullable String[] paramNames, Executable executable,
			boolean autowiring, boolean fallback) throws UnsatisfiedDependencyException {

		//获取bean工厂的自定义的TypeConverter
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		//如果customeConverter不为null,converter就引用customeConverter，否则引用bw
		TypeConverter converter = (customConverter != null ? customConverter : bw);

		//根据paramTypes的数组长度构建一个ArgumentsHolder实例，用于保存解析后的参数值
		ArgumentsHolder args = new ArgumentsHolder(paramTypes.length);
		//定义一个用于存储构造函数参数值Holder，以查找下一个任意泛型参数值时，忽略该集合的元素的HashSet,初始化长度为paramTypes的数组长度
		Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
		//定义一个用于存储自动注入Bean名的LinkedHashSet
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);

		//fori形式遍历paramType,索引为paramIndex
		for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
			//获取paramTypes中第paramIndex个参数类型
			Class<?> paramType = paramTypes[paramIndex];
			//如果paramNames不为null，就引用第paramIndex个参数名否则引用空字符串
			String paramName = (paramNames != null ? paramNames[paramIndex] : "");
			// Try to find matching constructor argument value, either indexed or generic.
			//尝试找到匹配的构造函数参数值，无论是索引的还是泛型的
			//定义一个用于存储与paramIndex对应的ConstructorArgumentValues.ValueHolder实例
			ConstructorArgumentValues.ValueHolder valueHolder = null;
			//如果resolvedValues不为null
			if (resolvedValues != null) {
				//在resolvedValues中查找与paramIndex对应的参数值，或者按paramType一般匹配
				valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);
				// If we couldn't find a direct match and are not supposed to autowire,
				// let's try the next generic, untyped argument value as fallback:
				// it could match after type conversion (for example, String -> int).
				// 如果找不到直接匹配并且不希望自动装配，请尝试使用一个通用的，无类型的参数值作为后备：
				// 类型转换后可以匹配(例如String -> int)
				//如果valueHolder为null 且 (mbd不支持使用构造函数进行自动注入 或者 paramTypes数组长度与resolvedValues的
				// (索引参数值+泛型参数值)数量相等)
				if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
					//在resovledValues中查找任意，不按名称匹配参数值的下一个泛型参数值，而忽略usedValueHolders参数值
					valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
				}
			}
			//如果valueHolder不为null
			if (valueHolder != null) {
				// We found a potential match - let's give it a try.
				// Do not consider the same value definition multiple times!
				// 我们找到了可能的匹配-让我们尝试一些。
				// 不要考虑相同的值定义
				//将valueHolder添加到usedValueHolders中，以表示该valueHolder已经使用过，下次在resolvedValues中
				// 获取下一个valueHolder时，不要返回同一个对象
				usedValueHolders.add(valueHolder);
				//从valueHolder中获取原始参数值
				Object originalValue = valueHolder.getValue();
				//定义一个用于存储转换后的参数值的Object对象
				Object convertedValue;
				//如果valueHolder已经包含转换后的值
				if (valueHolder.isConverted()) {
					//从valueHolder中获取转换后的参数值
					convertedValue = valueHolder.getConvertedValue();
					//将convertedValue保存到args的preparedArguments数组的paramIndex对应元素中
					args.preparedArguments[paramIndex] = convertedValue;
				}
				else {
					//将executable中paramIndex对应的参数封装成MethodParameter对象
					MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
					try {
						//使用converter将originalValue转换为paramType类型
						convertedValue = converter.convertIfNecessary(originalValue, paramType, methodParam);
					}
					//捕捉在转换类型时出现的类型不匹配异常
					catch (TypeMismatchException ex) {
						//重新抛出不满足的依赖异常：无法将类型[valueHolder.getValue（）的类名]的参数值转换为所需的类型[paramType.getName（）]
						throw new UnsatisfiedDependencyException(
								mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
								"Could not convert argument value of type [" +
										ObjectUtils.nullSafeClassName(valueHolder.getValue()) +
										"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
					}
					//获取valueHolder的源对象，一般是ValueHolder
					Object sourceHolder = valueHolder.getSource();
					//如果sourceHolder是ConstructorArgumentValues.ValueHolder实例
					if (sourceHolder instanceof ConstructorArgumentValues.ValueHolder) {
						//将soureHolder转换为ConstructorArgumentValues.ValueHolder对象
						Object sourceValue = ((ConstructorArgumentValues.ValueHolder) sourceHolder).getValue();
						//将args的resolveNecessary该为true，表示args.preparedArguments需要解析
						args.resolveNecessary = true;
						//将sourceValue保存到args的preparedArguments数组的paramIndex对应元素中
						args.preparedArguments[paramIndex] = sourceValue;
					}
				}
				//将convertedValue保存到args的arguments数组的paramIndex对应元素中
				args.arguments[paramIndex] = convertedValue;
				//将originalValue保存到args的rawArguments数组的paramIndex对应元素中
				args.rawArguments[paramIndex] = originalValue;
			}
			else {//否则(valueHolder不为null)
				//将executable中paramIndex对应的参数封装成MethodParameter对象
				MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
				// No explicit match found: we're either supposed to autowire or
				// have to fail creating an argument array for the given constructor.
				// 找不到明确的匹配项:我们要么自动装配，要么必须为给定的构造函数创建参数数组而失败
				//mbd不支持适用构造函数进行自动注入
				if (!autowiring) {
					//抛出不满足依赖异常:类型为[paramType.getName]的参数的参数值不明确-您是否指定了正确的bean引用
					// 作为参数？
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
							"Ambiguous argument values for parameter of type [" + paramType.getName() +
							"] - did you specify the correct bean references as arguments?");
				}
				try {
					//解析应该自动装配的methodParam的Bean对象,使用autowiredBeanNames保存所找到的所有候选Bean对象
					Object autowiredArgument = resolveAutowiredArgument(
							methodParam, beanName, autowiredBeanNames, converter, fallback);
					//将autowiredArgument保存到args的rawArguments数组的paramIndex对应元素中
					args.rawArguments[paramIndex] = autowiredArgument;
					//将autowiredArgument保存到args的arguments数组的paramIndex对应元素中
					args.arguments[paramIndex] = autowiredArgument;
					//将autowiredArgumentMarker保存到args的arguments数组的paramIndex对应元素中
					args.preparedArguments[paramIndex] = autowiredArgumentMarker;
					//将args的resolveNecessary该为true，表示args.preparedArguments需要解析
					args.resolveNecessary = true;
				}
				//捕捉解析应该自动装配的methodParam的Bean对象时出现的BeanException
				catch (BeansException ex) {
					//重新抛出满足依赖异常，引用mbd的资源描述作为异常信息
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam), ex);
				}
			}
		}

		//遍历 autowiredBeanNames，元素为autowiredBeanName
		for (String autowiredBeanName : autowiredBeanNames) {
			//注册beanName与dependentBeanNamed的依赖关系到beanFactory中
			this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
			//如果当前日志级别时debug
			if (logger.isDebugEnabled()) {
				//打印debug日志:通过构造器/工厂方法按类型从Bean名'beanName'自动注入到名为'autowiredBeanName'的bean
				logger.debug("Autowiring by type from bean name '" + beanName +
						"' via " + (executable instanceof Constructor ? "constructor" : "factory method") +
						" to bean named '" + autowiredBeanName + "'");
			}
		}
		//将args(保存着解析后的参数值的ArgumentsHolder对象)返回出去
		return args;
	}

	/**
	 * <p>解析缓存在mbd中准备好的参数值:
	 *  <ol>
	 *   <li>获取bean工厂的自定义的TypeConverter【变量 customConverter】</li>
	 *   <li>如果customeConverter不为null,converter就引用customeConverter，否则引用bw</li>
	 *   <li>新建一个BeanDefinitionValue解析器对象【变量 valueResolver】</li>
	 *   <li>从executable中获取其参数类型数组【变量 paramTypes】</li>
	 *   <li>定义一个解析后的参数值数组,长度argsToResolve的长度【变量 resolvedArgs】</li>
	 *   <li>遍历argsToResolvefori形式)：
	 *    <ol>
	 *     <li>获取argsToResolver的第argIndex个参数值【变量 argValue】</li>
	 *     <li>为executable的argIndex位置参数创建一个新的MethodParameter对象【变量 methodParam】</li>
	 *     <li><b>如果agrValue是自动装配的参数标记【{@link #autowiredArgumentMarker}】</b>:,解析出应该自动装配的methodParam
	 *     的Bean对象【{@link #resolveAutowiredArgument(MethodParameter, String, Set, TypeConverter, boolean)}】</li>
	*     <li><b>如果argValue是BeanMetadataElement对象</b>:交由valueResolver解析出value所封装的对象【{@link BeanDefinitionValueResolver#resolveValueIfNecessary(Object, Object)}】</li>
	 *     <li><b>如果argValue是String对象</b>:评估benaDefinition中包含的argValue,如果argValue是可解析表达式，会对其进行解析，否
	 *     则得到的还是argValue【{@link AbstractBeanFactory#evaluateBeanDefinitionString(String, BeanDefinition)}】</li>
	 *     <li>获取第argIndex个的参数类型【变量 paramType】</li>
	 *     <li>将argValue转换为paramType类型对象并赋值给第i个resolvedArgs元素</li>
	 *     <li>捕捉转换类型时抛出的类型不匹配异常,抛出不满意的依赖异常</li>
	 *    </ol>
	 *   </li>
	 *   <li>返回解析后的参数值数组【resolvedArgs】</li>
	 *  </ol>
	 * </p>
	 * Resolve the prepared arguments stored in the given bean definition.
	 * <p>解析缓存在给定bean定义中的准备好的参数</p>
	 * @param beanName bean名
	 * @param mbd beanName对于的合并后RootBeanDefinition
	 * @param bw bean的包装类，此时bw还没有拿到bean
	 * @param executable mbd已解析的构造函数或工厂方法对象
	 * @param argsToResolve mbd部分准备好的构造函数参数值
	 * @param fallback 是否可在抛出NoSuchBeanDefinitionException返回null，而不抛出异常
	 */
	private Object[] resolvePreparedArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			Executable executable, Object[] argsToResolve, boolean fallback) {

		//获取bean工厂的自定义的TypeConverter
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		//如果customeConverter不为null,converter就引用customeConverter，否则引用bw
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		//BeanDefinitionValueResolver主要是用于将bean定义对象中包含的值解析为应用于目标bean实例的实际值
		//新建一个BeanDefinitionValue解析器对象
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
		//从executable中获取其参数类型
		Class<?>[] paramTypes = executable.getParameterTypes();

		//定义一个解析后的参数值数组,长度argsToResolve的长度
		Object[] resolvedArgs = new Object[argsToResolve.length];
		//遍历argsToResolve(fori形式)
		for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
			//获取argsToResolver的第argIndex个参数值
			Object argValue = argsToResolve[argIndex];
			//为executable的argIndex位置参数创建一个新的MethodParameter对象
			MethodParameter methodParam = MethodParameter.forExecutable(executable, argIndex);
			//如果agrValue是自动装配的参数标记
			if (argValue == autowiredArgumentMarker) {
				//解析出应该自动装配的methodParam的Bean对象
				argValue = resolveAutowiredArgument(methodParam, beanName, null, converter, fallback);
			}
			//BeanMetadataElement:由包含配置源对象的bean元数据元素实现的接口,BeanDefinition的父接口
			//如果argValue是BeanMetadataElement对象
			else if (argValue instanceof BeanMetadataElement) {
				//交由valueResolver解析出value所封装的对象
				argValue = valueResolver.resolveValueIfNecessary("constructor argument", argValue);
			}
			//如果argValue是String对象
			else if (argValue instanceof String) {
				//评估benaDefinition中包含的argValue,如果argValue是可解析表达式，会对其进行解析，否则得到的还是argValue
				argValue = this.beanFactory.evaluateBeanDefinitionString((String) argValue, mbd);
			}
			//获取第argIndex个的参数类型
			Class<?> paramType = paramTypes[argIndex];
			try {
				//将argValue转换为paramType类型对象并赋值给第i个resolvedArgs元素
				resolvedArgs[argIndex] = converter.convertIfNecessary(argValue, paramType, methodParam);
			}
			//捕捉转换类型时抛出的类型不匹配异常
			catch (TypeMismatchException ex) {
				//抛出不满意的依赖异常:无法转换类型为[argValue的Class对象名]的参数值到所需的类型[paramType的Class对象名]
				throw new UnsatisfiedDependencyException(
						mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
						"Could not convert argument value of type [" + ObjectUtils.nullSafeClassName(argValue) +
						"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
			}
		}
		//返回解析后的参数值数组【resolvedArgs】
		return resolvedArgs;
	}

	protected Constructor<?> getUserDeclaredConstructor(Constructor<?> constructor) {
		Class<?> declaringClass = constructor.getDeclaringClass();
		Class<?> userClass = ClassUtils.getUserClass(declaringClass);
		if (userClass != declaringClass) {
			try {
				return userClass.getDeclaredConstructor(constructor.getParameterTypes());
			}
			catch (NoSuchMethodException ex) {
				// No equivalent constructor on user class (superclass)...
				// Let's proceed with the given constructor as we usually would.
			}
		}
		return constructor;
	}

	/**
	 * <p>解析应该自动装配的指定参数的Bean对象:
	 *  <ol>
	 *   <li>获取param的参数类型【变量 paramType】</li>
	 *   <li>如果paramType属于InjectionPoint:
	 *    <ol>
	 *     <li>从线程本地中获取当前切入点对象【变量 injectionPoint】</li>
	 *     <li>如果injectionPoint为null,抛出非法状态异常:当前没有InjectionPoint可用于param</li>
	 *     <li>返回当前injectionPoint对象【injectionPoint】</li>
	 *    </ol>
	 *   </li>
	 *   <li>将param封装成DependencyDescriptor对象，让当前Bean工厂根据该DependencyDescriptor对象的依赖类型解析出与
	 *   该DependencyDescriptor对象所包装的对象匹配的候选Bean对象，然后返回出去:
	 *    <ol>
	 *     <li>捕捉没有唯一的BeanDefinition异常,重写抛出该异常</li>
	 *     <li>捕捉 没有此类beanDefintion异常【变量 ex】:
	 *      <ol>
	 *       <li>如果可在没有此类BeanDefintion的时候回退【fallback】:
	 *        <ol>
	 *         <li>如果paramType是数组类型,根据参数数组的元素类型新建一个空数组对象</li>
	 *         <li>如果paramType是否是常见的Collection类,根据参数类型创建对应一个空的Collection对象</li>
	 *         <li>如果paramType是否是常见的Map类，根据paramType创建对应一个空的Map对象</li>
	 *        </ol>
	 *       </li>
	 *       <li>不可以回退，或者参数类型不是常见数组/集合类型时，重新抛出异常【ex】</li>
	 *      </ol>
	 *     </li>
	 *    </ol>
	 *   </li>
	 *  </ol>
	 * </p>
	 * Template method for resolving the specified argument which is supposed to be autowired.
	 * <p>用于解析应该自动装配的指定参数的模板方法</p>
	 * @param param 方法参数封装对象
	 * @param beanName bean名
	 * @param autowiredBeanNames 一个集合，所有自动装配的bean名(用于解决给定依赖关系)都应添加.即自动注入匹配成功的候选Bean名集合。
	 *                              【当autowiredBeanNames不为null，会将所找到的所有候选Bean对象添加到该集合中,以供调用方使用
	 * @param typeConverter 类型装换器
	 * @param fallback 是否可在抛出NoSuchBeanDefinitionException返回null，而不抛出异常
	 */
	@Nullable
	protected Object resolveAutowiredArgument(MethodParameter param, String beanName,
			@Nullable Set<String> autowiredBeanNames, TypeConverter typeConverter, boolean fallback) {
		//获取param的参数类型
		Class<?> paramType = param.getParameterType();
		//InjectionPoint用于描述一个AOP注入点
		//如果paramType属于InjectionPoint
		if (InjectionPoint.class.isAssignableFrom(paramType)) {
			//从线程本地中获取当前切入点对象，该对象一般在Bean工厂解析出与descriptor所包装的对象匹配的候选Bean对象的时候设置
			InjectionPoint injectionPoint = currentInjectionPoint.get();
			//如果injectionPoint为null
			if (injectionPoint == null) {
				//抛出非法状态异常:当前没有InjectionPoint可用于param
				throw new IllegalStateException("No current InjectionPoint available for " + param);
			}
			//返回当前injectionPoint对象
			return injectionPoint;
		}
		try {
			//DependencyDescriptor：即将注入的特定依赖项描述符。包装构造函数，方法参数或字段，以允许对其元数据 的统一访问
			//该DependencyDescriptor对象的依赖类型就是指param的类型
			//将param封装成DependencyDescriptor对象，让当前Bean工厂根据该DependencyDescriptor对象的依赖类型解析出与
			// 	该DependencyDescriptor对象所包装的对象匹配的候选Bean对象，然后返回出去
			return this.beanFactory.resolveDependency(
					new DependencyDescriptor(param, true), beanName, autowiredBeanNames, typeConverter);
		}
		//捕捉没有唯一的BeanDefinition异常
		catch (NoUniqueBeanDefinitionException ex) {
			//重写抛出该异常
			throw ex;
		}
		//捕捉 没有此类beanDefintion异常
		catch (NoSuchBeanDefinitionException ex) {
			//如果可在没有此类BeanDefintion的时候回退
			if (fallback) {
				// Single constructor or factory method -> let's return an empty array/collection
				// for e.g. a vararg or a non-null List/Set/Map parameter.
				// 单一构造函数或工厂方法->让我们返回一个空数组/集合，例如vararg或非null的List/Set/Map对象
				//如果参数类型是数组类型
				if (paramType.isArray()) {
					//根据参数数组的元素类型新建一个空数组对象
					return Array.newInstance(paramType.getComponentType(), 0);
				}
				//如果paramType是否是常见的Collection类
				else if (CollectionFactory.isApproximableCollectionType(paramType)) {
					//根据参数类型创建对应的Collection对象
					return CollectionFactory.createCollection(paramType, 0);
				}
				//如果paramType是否是常见的Map类
				else if (CollectionFactory.isApproximableMapType(paramType)) {
					//根据参数类型创建对应的Map对象
					return CollectionFactory.createMap(paramType, 0);
				}
			}
			//不可以回退，或者参数类型不是常见数组/集合类型时，重新抛出异常
			throw ex;
		}
	}

	/**
	 * 设置新得当前切入点对象，返回旧的当前切入点对象
	 * <ol>
	 *  <li>从线程本地的当前切入点对象【currentInjectionPoint】中获取旧的InjectionPoint对象【变量 old】</li>
	 *  <li>如果injectionPoint不为null,将injectionPoint设置到currentInjectionPoint;否则移除currentInjectionPoint
	 *  所存储得InjectionPoint对象</li>
	 *  <li>返回old</li>
	 * </ol>
	 * @param injectionPoint 新的切入点
	 * @return 返回旧的当前切入点对象，如果没有返回null
	 */
	static InjectionPoint setCurrentInjectionPoint(@Nullable InjectionPoint injectionPoint) {
		//从线程本地的当前切入点对象中获取旧的InjectionPoint对象
		InjectionPoint old = currentInjectionPoint.get();
		//如果injectionPoint不为null
		if (injectionPoint != null) {
			//将injectionPoint设置成当前切入点对象
			currentInjectionPoint.set(injectionPoint);
		}
		else {
			//移除当前切入点对象
			currentInjectionPoint.remove();
		}
		//返回旧得切入带你对象
		return old;
	}


	/**
	 * Private inner class for holding argument combinations.
	 * <p>私有内部类，用于保存参数组合</p>
	 */
	private static class ArgumentsHolder {

		/**
		 * 原始参数值数组
		 */
		public final Object[] rawArguments;

		/**
		 * 经过转换后参数值数组
		 */
		public final Object[] arguments;

		/**
		 * 准备好的参数值数组，保存着 由解析的自动装配参数替换的标记和源参数值
		 */
		public final Object[] preparedArguments;

		/**
		 * 需要解析的标记，默认为false
		 */
		public boolean resolveNecessary = false;

		public ArgumentsHolder(int size) {
			this.rawArguments = new Object[size];
			this.arguments = new Object[size];
			this.preparedArguments = new Object[size];
		}

		public ArgumentsHolder(Object[] args) {
			this.rawArguments = args;
			this.arguments = args;
			this.preparedArguments = args;
		}

		/**
		 * 获取类型差异权重，宽容模式下使用
		 * <ol>
		 *  <li>获取表示paramTypes和arguments之间的类层次结构差异的权重【变量 typeDiffWeight】</li>
		 *  <li>获取表示paramTypes和rawArguments之间的类层次结构差异的权重【变量 rawTypeDiffWeight】</li>
		 *  <li>比较typeDiffWeight和rawTypeDiffWeight取最小权重并返回出去，但是还是以原始类型优先，因为差异值还-1024</li>
		 * </ol>
		 * @param paramTypes 参数类型数组
		 * @return 类型差异权重最小值
		 */
		public int getTypeDifferenceWeight(Class<?>[] paramTypes) {
			// If valid arguments found, determine type difference weight.
			// Try type difference weight on both the converted arguments and
			// the raw arguments. If the raw weight is better, use it.
			// Decrease raw weight by 1024 to prefer it over equal converted weight.
			// 如果找到有效的参数，请确定类型差异权重。尝试对转换后的参数和原始参数都使用类型差异权重。如果
			// 原始重量更好，请使用它。将原始重量减少1024，以使其优于相等的转换重量。
			//MethodInvoker.getTypeDifferenceWeight-确定表示类型和参数之间的类层次结构差异的权重：
			//1. arguments的类型不paramTypes类型的子类，直接返回 Integer.MAX_VALUE,最大重量，也就是直接不匹配
			//2. paramTypes类型是arguments类型的父类则+2
			//3. paramTypes类型是arguments类型的接口，则+1
			//4. arguments的类型直接就是paramTypes类型,则+0
			//获取表示paramTypes和arguments之间的类层次结构差异的权重
			int typeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.arguments);
			//获取表示paramTypes和rawArguments之间的类层次结构差异的权重
			int rawTypeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.rawArguments) - 1024;
			//取最小权重，但是还是以原始类型优先，因为差异值还-1024
			return Math.min(rawTypeDiffWeight, typeDiffWeight);
		}

		/**
		 * 获取Assignabliity权重，严格模式下使用
		 * <ol>
		 *  <li>fori形式遍历paramTypes:
		 *   <ol>
		 *    <li>如果确定arguments不是paramTypes的实例,返回Integer最大值;意味着既然连最终的转换后参数值都不能匹配，这个情况下
		 *    paramTypes所对应的工厂方法是不可以接受的</li>
		 *   </ol>
		 *  </li>
		 *  <li>fori形式遍历paramTypes:
		 *   <ol>
		 *    <li>如果确定rawArguments不是paramTypes的实例,返回Integer最大值-512;意味着虽然转换后的参数值匹配，但是原始的参数值不匹配，
		 *    这个情况下的paramTypes所对应的工厂方法还是可以接受的</li>
		 *   </ol>
		 *  </li>
		 *  <li>在完全匹配的情况下，返回Integer最大值-1024；意味着因为最终的转换后参数值和原始参数值都匹配，
		 *  这种情况下paramTypes所对应的工厂方法非常可以接收</li>
		 * </ol>
		 * <p>补充：为啥这里使用Integer.MAX_VALUE作为最初比较值呢？我猜测是因为业务比较是才有谁小谁优先原则。至于为啥-512，和-1024呢？这个我也没懂，但
		 * 至少-512，-1024所得到结果比起-1，-2的结果会明显很多。</p>
		 * @param paramTypes 参数类型
		 * @return Assignabliity权重
		 */
		public int getAssignabilityWeight(Class<?>[] paramTypes) {
			//fori形式遍历paramTypes
			for (int i = 0; i < paramTypes.length; i++) {
				//如果确定arguments不是paramTypes的实例
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.arguments[i])) {
					//返回Integer最大值，意味着既然连最终的转换后参数值都不能匹配，这个情况下的paramTypes所对应的工厂方法是不可以接受的
					return Integer.MAX_VALUE;
				}
			}
			//fori形式遍历paramTypes
			for (int i = 0; i < paramTypes.length; i++) {
				//如果确定rawArguments不是paramTypes的实例
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.rawArguments[i])) {
					//返回Integer最大值-512，意味着虽然转换后的参数值匹配，但是原始的参数值不匹配，这个情况下的paramTypes所对应的工厂方法还是可以接受的
					return Integer.MAX_VALUE - 512;
				}
			}
			//在完全匹配的情况下，返回Integer最大值-1024；意味着因为最终的转换后参数值和原始参数值都匹配，这种情况下paramTypes所对应的工厂方法非常可以接收
			return Integer.MAX_VALUE - 1024;
		}

		/**
		 * 将ArgumentsHolder所得到的参数值属性缓存到mbd对应的属性中：
		 * <ol>
		 *  <li>使用mbd的构造函数通用锁【{@link RootBeanDefinition#constructorArgumentLock}】加锁以保证线程安全:
		 *   <ol>
		 *    <li>让mbd的已解析的构造函数或工厂方法【{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}】引用constructorOrFactoryMethod</li>
		 *    <li>将mdb的构造函数参数已解析标记【{@link RootBeanDefinition#constructorArgumentsResolved}】设置为true</li>
		 *    <li>如果resolveNecessary为true，表示参数还需要进一步解析:
		 *     <ol>
		 *      <li>让mbd的缓存部分准备好的构造函数参数值属性【{@link RootBeanDefinition#preparedConstructorArguments}】引用preparedArguments</li>
		 *      <li>让mbd的缓存完全解析的构造函数参数属性【{@link RootBeanDefinition#resolvedConstructorArguments}】引用arguments</li>
		 *     </ol>
		 *    </li>
		 *   </ol>
		 *  </li>
		 * </ol>
		 * @param mbd bean对象的合并后RootBeanDefinition
		 * @param constructorOrFactoryMethod 匹配的构造函数方法
		 */
		public void storeCache(RootBeanDefinition mbd, Executable constructorOrFactoryMethod) {
			//使用mbd的构造函数通用锁【{@link RootBeanDefinition#constructorArgumentLock}】加锁以保证线程安全
			synchronized (mbd.constructorArgumentLock) {
				//让mbd的已解析的构造函数或工厂方法【{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}】引用constructorOrFactoryMethod
				mbd.resolvedConstructorOrFactoryMethod = constructorOrFactoryMethod;
				//将mdb的构造函数参数已解析标记【{@link RootBeanDefinition#constructorArgumentsResolved}】设置为true
				mbd.constructorArgumentsResolved = true;
				//如果resolveNecessary为true，表示参数还需要进一步解析
				if (this.resolveNecessary) {
					//让mbd的缓存部分准备好的构造函数参数值属性【{@link RootBeanDefinition#preparedConstructorArguments}】引用preparedArguments
					mbd.preparedConstructorArguments = this.preparedArguments;
				}
				else {
					//让mbd的缓存完全解析的构造函数参数属性【{@link RootBeanDefinition#resolvedConstructorArguments}】引用arguments
					mbd.resolvedConstructorArguments = this.arguments;
				}
			}
		}
	}


	/**
	 * Delegate for checking Java 6's {@link ConstructorProperties} annotation.
	 * <p>用于检查Java 6的{@link ConstructorProperties}注解的委托类</p>
	 * <p>参考博客：https://blog.csdn.net/m0_37668842/article/details/82664680</p>
	 */
	private static class ConstructorPropertiesChecker {

		/**
		 * 获取candidate的ConstructorProperties注解的name属性值
		 * <ol>
		 *  <li>获取candidated中的ConstructorProperties注解 【变量 cp】</li>
		 *  <li>如果cp不为null:
		 *   <ol>
		 *    <li>获取cp指定的getter方法的属性名 【变量 names】</li>
		 *    <li>如果names长度于paramCount不相等,抛出IllegalStateException</li>
		 *    <li>将name返回出去</li>
		 *   </ol>
		 *  </li>
		 *  <li>如果没有配置ConstructorProperties注解，则返回null</li>
		 * </ol>
		 * @param candidate 候选方法
		 * @param paramCount candidate的参数梳理
		 * @return candidate的ConstructorProperties注解的name属性值
		 */
		@Nullable
		public static String[] evaluate(Constructor<?> candidate, int paramCount) {
			//获取candidated中的ConstructorProperties注解
			ConstructorProperties cp = candidate.getAnnotation(ConstructorProperties.class);
			//如果cp不为null
			if (cp != null) {
				//获取cp指定的getter方法的属性名
				String[] names = cp.value();
				//如果names长度于paramCount不相等
				if (names.length != paramCount) {
					//抛出IllegalStateException:用@ConstructorPropertie注解的构造方法，不对应实际的参数数量(paramCount):candidate
					throw new IllegalStateException("Constructor annotated with @ConstructorProperties but not " +
							"corresponding to actual number of parameters (" + paramCount + "): " + candidate);
				}
				//将name返回出去
				return names;
			}
			else {
				//如果没有配置ConstructorProperties注解，则返回null
				return null;
			}
		}
	}

}


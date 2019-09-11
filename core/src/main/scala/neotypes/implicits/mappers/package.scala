package neotypes.implicits

package object mappers {
  final object all extends AllMappers
  final object executions extends ExecutionMappers
  final object parameters extends ParameterMappers
  final object results extends ResultMappers with CompabilityMappers
  final object values extends ValueMappers
}

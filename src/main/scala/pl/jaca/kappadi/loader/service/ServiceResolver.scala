package pl.jaca.kappadi.loader.service

import java.lang.invoke.{MethodHandle, MethodHandles}
import java.lang.reflect.{AnnotatedElement, Constructor, Parameter}

import pl.jaca.kappadi.loader.service.ServiceResolver.{AmbiguousServiceBuilderDefinition, NoServiceConstructorFoundException, UnableToResolveServiceBuilderException}
import pl.jaca.kappadi.service.{Qualified, ServiceBuilder}

/**
  * @author Jaca777
  *         Created 2017-08-27 at 22
  */
private[loader] class ServiceResolver(classResolver: ClassResolver) {

  def resolveServiceInfo(): Set[ServiceInfo] = {
    val classes = classResolver.resolveServiceClasses().par
    classes.map(toDeclaredServiceInfo).seq
  }

  def toDeclaredServiceInfo(serviceClass: Class[_]): ServiceInfo = {
    val builderConstructor = findBuilderConstructor(serviceClass)
    val builderHandle = toMethodHandle(builderConstructor)
    val dependencies = getDependencies(builderConstructor)
    val qualifier = getQualifier(serviceClass)
    ServiceInfo(serviceClass, qualifier, builderHandle, dependencies.toList)
  }

  private def findBuilderConstructor(serviceClass: Class[_]): Constructor[_] =
    serviceClass.getConstructors match {
      case Array(constr) => constr
      case constrs: Array[Constructor[_]] =>
        getExplicitBuilderConstructor(constrs, serviceClass.getName)
      case Array() =>
        throw NoServiceConstructorFoundException(serviceClass.getName)
    }

  private def getExplicitBuilderConstructor(constrs: Array[Constructor[_]], className: String): Constructor[_] = {
    val explicitConstrs = constrs.filter(isExplicitBuilder)
    explicitConstrs match {
      case Array(constr) => constr
      case a if a.length > 1 =>
        throw AmbiguousServiceBuilderDefinition(className)
      case Array() =>
        throw UnableToResolveServiceBuilderException(className)
    }
  }

  private def isExplicitBuilder(constructor: Constructor[_]): Boolean =
    constructor.getAnnotation(classOf[ServiceBuilder]) != null

  private def toMethodHandle(constructor: Constructor[_]): MethodHandle = {
    val lookup = MethodHandles.lookup()
    lookup.unreflectConstructor(constructor)
  }

  private def getDependencies(constr: Constructor[_]): Array[DependencyInfo] ={
    val params = constr.getParameters
    params.map(toDependency)
  }

  private def toDependency(param: Parameter): DependencyInfo =
    DependencyInfo(param.getType, getQualifier(param))

  private def getQualifier(param: AnnotatedElement): Option[String] = {
    val annotation = param.getAnnotation(classOf[Qualified])
    if(annotation != null) {
      Some(annotation.qualifier())
    } else {
      None
    }
  }
}

object ServiceResolver {
  case class NoServiceConstructorFoundException(className: String)
    extends RuntimeException(
      s"No public constructors found for service at $className"
    )

  case class AmbiguousServiceBuilderDefinition(className: String)
    extends RuntimeException(
      s"Multiple constructors annotated with @ServiceBuilder found for service at $className"
    )

  case class UnableToResolveServiceBuilderException(className: String)
    extends RuntimeException(
      s"Multiple constructors for service at $className found, but none is annotated with @ServiceBuilder"
    )

}
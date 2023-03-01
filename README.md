[![Community badge: Incubating](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
[![Community extension badge](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)

# Kotlin-coworker

This project aims to provide a neat Kotlin Coroutines API to Zeebe Gateway. Right now there is just a worker coroutine API.

## Motivation

I decided to create it in trying to gain all performance that I can gain using Kotlin Coroutines stack.
So, if you replace blocking calls with Coroutines suspension you can take more jobs in parallel, instead of one (in Zeebe Client Java default settings).

You can see [the performance comparison test for yourself](benchmark/src/test/kotlin/org/camunda/community/extension/coworker/CoworkerVsJobWorkerPerformanceComparisonTest.kt), but in my machine, the numbers are next:
```
For Zeebe Client it took 41.186149961s to process 40
...
For Coworker it took 1.522231647s to process 40
So, Zeebe Client Java duration / Coworker duration = 27.056427346106805
```

So, the same worker with delay, but in the reactive stack takes in **27 times less** time to complete process instances.

## Usage

* Add the dependency
```xml
<dependency>
    <groupId>org.camunda.community.extension.kotlin.coworker</groupId>
    <artifactId>coworker-core</artifactId>
    <version>x.y.z</version>
</dependency>
```
* Obtain a `ZeebeClient` instance, for example, `ZeebeClient.newClient()`
* Use the extension function to obtain a `Cozeebe` instance `zeebeClient.toCozeebe()`
* Create a new Coworker instance:
```kotlin
val coworker = cozeebe.newCoWorker(jobType, object : JobHandler {
    override suspend fun handle(client: JobClient, job: ActivatedJob) {
        val variables = job.variablesAsMap
        val aVar = variables["a"] as Int
        val bVar = variables["b"] as Int
        variables["c"] = aVar + bVar

        client.newCompleteCommand(job).variables(variables).send().await()
    }
})
```
* Open it, like Zeebe's Java Worker: `coworker.open()`

## Features

- Coroutine native implementation (you can use suspend functions inside `JobHandler` methods)
- Easily combine with existing Zeebe Client Java libs.
- Because of using coroutines Coworker could activate more jobs containing blocking logic (Database queries, HTTP REST calls, etc.) if they adopted coroutines (a.k.a non-blocking API) than a classic Zeebe Java worker. You can see results for yourself in the benchmark module.
- Spring Boot Starter

### Spring Boot Starter

It requires:
- Spring Boot 2.7.+ (should work with Spring Boot 3.0.x but haven't tested properly).
- JDK 11

First, you need to add dependency:
```xml
<dependency>
    <groupId>org.camunda.community.extension.kotlin.coworker</groupId>
    <artifactId>coworker-spring-boot-starter</artifactId>
    <version>x.y.z</version>
</dependency>
```

Then, if you need to define Zeebe Worker with coroutines, like this:
```kotlin
@Coworker(type = "test")
suspend fun testWorker(jobClient: JobClient, job: ActivatedJob) {
  someService.callSomeSuspendMethod(job.variables)
  jobClient.newCompleteCommand(activatedJob.key).send().await()
}
```

Note:
1. Method should be `suspend`
2. Method should be annotated with `@Coworker`
3. Method should not call thread-blocking functions. Use Kotlin's `.await()` instead of `.join()` in the example upward.
4. It hasn't had all the features from Spring Zeebe, but it seems that some features will be ported eventually. Create an issue or PR with the feature that you need :)

### Override coroutine context for each coworker execution

Sometimes you need to provide some data in a coroutine context (an MDC map, for example) based on the job.
To do so, you have to override `additionalCoroutineContextProvider` from `JobCoworkerBuilder`. Something, like this:

```kotlin
client.toCozeebe().newCoWorker(jobType) { client, job ->
            // your worker logic
            client.newCompleteCommand(job).send().await()
        }
            // override additionalCoroutineContextProvider
            .also { it.additionalCoroutineContextProvider = JobCoroutineContextProvider { testCoroutineContext } }
            // open worker
            .open().use {
                // logic to keep the worker running
            }
```

If you are using the Spring Boot Starter, you need just to create a bean with the type `JobCoroutineContextProvider` in your Spring context. Like this:
```kotlin
    @Bean
    fun loggingJobCoroutineContextProvider(): JobCoroutineContextProvider {
        return JobCoroutineContextProvider {
          MDCContext()
        }
    }
```

### Custom error handling

Sometimes, you want to override the default error handling mechanism. To do so, you need to customize your worker like this:

```kotlin
        client.toCozeebe().newCoWorker(jobType) { job: ActivatedJob, jobClient: JobClient ->
            // worker's logic
        }
            .also {
                // override job error handler
                it.jobErrorHandler = JobErrorHandler { e, activatedJob, jobClient ->
                    if (e is IgnorableException) {
                        jobClient.newCompleteCommand(activatedJob).variables(mapOf("ignored" to true)).send().await()
                    } else {
                        jobClient.newFailCommand(activatedJob).retries(activatedJob.retries - 1).send().await()
                    }
                }
            }
```

#### Error handling in Spring Boot

If you are using the Spring Boot Starter, you need to define a `JobErrorHandler` bean in your context:
```kotlin
        @Bean
        open fun customErrorHandler(): JobErrorHandler {
            val defaultErrorHandler = DefaultSpringZeebeErrorHandler()
            return JobErrorHandler { e, activatedJob, jobClient ->
                logger.error(e) { "Got error: ${e.message}, on job: $activatedJob" }
                defaultErrorHandler.handleError(e, activatedJob, jobClient)
            }
        }
```

**Warning**: It is highly recommend to use the `DefaultSpringZeebeErrorHandler` wrapper to wrap your error handling logic. More info in: https://github.com/camunda-community-hub/kotlin-coworker/issues/54

### Override annotation parameters via configuration properties

This works basically [the same as in the Spring Zeebe project](https://github.com/camunda-community-hub/spring-zeebe#overriding-jobworker-values-via-configuration-file).
So, you can override values in the `@Coworker` annotation with type foo like this:
```properties
zeebe.client.worker.override.foo.enabled=false
```

*Note*: you can't use the SpEL and properties placeholders in this value. You should return the same type in the `@Coworker` annotation.
The exception is `Duration`. You should return `Long` values in milliseconds.

### Annotation parameters

If you want to redefine `org.camunda.community.extension.coworker.spring.annotation.Coworker` parameters, you should use [SPeL](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions) to define annotation values.
See the property's JavaDoc for the type that should be resolved.
Also, you may use property placeholders (`${}`) in the annotation parameters to replace them with configuration properties if needed.

As an example you may refer to [the test](coworker-spring-boot-starter/src/test/kotlin/org/camunda/community/extension/coworker/spring/property/SpelValuesCoworkerIntegrationTest.kt).

## Missing Features

* Coroutines native `JobClient`

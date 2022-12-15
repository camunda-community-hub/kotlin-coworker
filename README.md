[![Community badge: Incubating](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
[![Community extension badge](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)

# Coworker

This project aims to provide a neat Kotlin Coroutines API to Zeebe Gateway. Right now there is just a worker coroutine API.

## Usage

* Add the dependency
```xml
    <dependency>
      <groupId>org.camunda.community.extension.coworker</groupId>
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

## Missing Features

* Spring (or Spring Boot) Integration
* Coroutines native `JobClient`

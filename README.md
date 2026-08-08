# hollowcube compat

Mod compatibility layer by [Hollow Cube Mapmaker](https://github.com/hollow-cube/mapmaker/) for on a [Minestom](https://minestom.net/) server. Provides client mod compat (Axiom, MoulberryTweaks, Noxesium) and the supporting packet/event API made by Hollowcube.
Not a full port.

## Usage

```kotlin
repositories {
    maven("https://mvn.everbuild.org/public")
}

dependencies {
    implementation("net.hollowcube:compat:1.0.0")
}
```

```java
// Load all compat providers discovered via ServiceLoader
CompatProvider.load(MinecraftServer.getGlobalEventHandler());
```

Requires Java 25.

## Development

Credentials for `mvn.everbuild.org` go in `gradle.properties` (`maven.username` / `maven.password`) or a gitignored `local.properties`.

```sh
./gradlew build
./gradlew publish
```

## Origin

Forked from the client-agnostic compat code shared by [hollow-cube/mapmaker](https://github.com/hollow-cube/mapmaker/), licensed under MIT.

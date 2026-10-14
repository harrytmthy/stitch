import org.gradle.plugins.signing.Sign

// Use only for the local consumer test. Release signing remains unchanged.
require(gradle.startParameter.taskNames.all {
    it.substringAfterLast(":").startsWith("publish") && it.endsWith("ToMavenLocal")
})
allprojects {
    tasks.withType<Sign>().configureEach { enabled = false }
}

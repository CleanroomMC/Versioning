package com.cleanroommc.versioning.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Produces only console output")
public abstract class PrintVersionTask extends DefaultTask {

    @Input
    public abstract Property<String> getVersion();

    @TaskAction
    public final void printVersion() {
        getLogger().quiet(getVersion().get());
    }

}

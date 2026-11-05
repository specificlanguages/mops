package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(
    name = "create",
    header = ["Create a project module or model."],
    description = [
        "Create a project module or model through the daemon and register it with the project. Each module lands " +
            "under the conventional directory for its kind (languages/<name>/<name>.mpl, " +
            "solutions/<name>/<name>.msd, and so on) unless --descriptor names the descriptor file. --dry-run " +
            "validates and prints what would be created without changing the project.",
    ],
)
class CreateOperations : CommandGroup()

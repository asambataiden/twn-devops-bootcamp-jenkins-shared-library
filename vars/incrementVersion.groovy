import com.asambataiden.devops.versioning.SemanticVersion
import com.asambataiden.devops.validation.InputValidator

/** Calculate a version only. Never writes the workspace or pushes to Git. */
def call(Map args = [:]) {
    if (!(args.version instanceof String)) {
        error('incrementVersion requires version: String')
    }
    String type = InputValidator.requireBumpType(args.type as String)
    return SemanticVersion.parse(args.version).bump(type).toString()
}

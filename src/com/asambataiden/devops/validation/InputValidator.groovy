package com.asambataiden.devops.validation

class InputValidator implements Serializable {
    static String requireBumpType(String type) {
        if (!(type in ['major', 'minor', 'patch'])) {
            throw new IllegalArgumentException('Supported version increments: major, minor, patch')
        }
        return type
    }
}

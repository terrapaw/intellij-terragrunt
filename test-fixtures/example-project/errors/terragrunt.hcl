# This file contains intentional errors for testing linting

# ERROR: Unknown block type
foobar {
  something = "value"
}

# ERROR: dependency missing required config_path
dependency "broken" {
  skip_outputs = true
}

# WARNING: deprecated attribute
dependency "old_style" {
  config_path = "../vpc"
  mock_outputs_merge_with_state = true
}

# WARNING: unresolved path
include "missing" {
  path = "../nonexistent/file.hcl"
}

# Valid for comparison
locals {
  valid = "this is fine"
}

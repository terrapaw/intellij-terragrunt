# This file demonstrates all inspections the plugin can detect.
# Each section shows a different type of warning.

# Unknown block type
foobar {
  x = 1
}

# Duplicate block name
dependency "vpc" {
  config_path = "../vpc"
}

dependency "vpc" {
  config_path = "../other-vpc"
}

# Unknown attribute in block
dependency "rds" {
  config_pathh = "../rds"
  mock_outputs = {}
}

# Missing required attribute (dependency needs config_path)
dependency "app" {
}

# Deprecated attribute
dependency "cache" {
  config_path                   = "../cache"
  mock_outputs_merge_with_state = true
}

# Unresolved variable reference
inputs = {
  x = local.does_not_exist
  y = dependency.nonexistent.outputs.id
}

# Unexpected label count
locals "should_not_have_label" {
  x = 1
}

# Too many labels — "extra_label" will be flagged
dependency "ec2" "extra_label" {
  config_path = "../vpc"
}

# Missing required label
dependency {
  config_path = "../vpc"
}

# Empty label
include "" {
  path = find_in_parent_folders("root.hcl")
}

# Suppressed — no warning shown (committable to source control)
# noinspection TerragruntUnresolvedPath
include "suppressed" {
  path = "../this-does-not-exist.hcl"
}

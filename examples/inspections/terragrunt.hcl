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

# Unused local variable — "unused_var" is never referenced via local.unused_var
locals {
  used_var   = "hello"
  unused_var = "nobody uses me"
}

inputs = {
  greeting = local.used_var
}

# Duplicate attribute — same key twice in same block
locals {
  name = "first"
  name = "second"
}

# Unused dependency — "ecs" label is never referenced via dependency.ecs
dependency "ecs" {
  config_path = "../ecs"
}

# Suppressed unused dependency
# noinspection TerragruntUnusedDependency
dependency "suppressed_dep" {
  config_path = "../suppressed"
}

# --- Autoinclude inspections (in stack files) ---
# These would appear in a terragrunt.stack.hcl file:

# locals not allowed in autoinclude
# unit "app" {
#   source = "./catalog/units/app"
#   path   = "app"
#   autoinclude {
#     locals { x = 1 }    # ERROR: locals blocks are not allowed in autoinclude
#   }
# }

# values not allowed in autoinclude
# unit "app" {
#   source = "./catalog/units/app"
#   path   = "app"
#   autoinclude {
#     values = { x = 1 }  # ERROR: values attribute is not allowed in autoinclude
#   }
# }

# Multiple autoinclude blocks in same unit
# unit "app" {
#   source = "./catalog/units/app"
#   path   = "app"
#   autoinclude { inputs = { x = 1 } }
#   autoinclude { inputs = { y = 2 } }  # ERROR: Only one autoinclude block is allowed per unit/stack
# }

# Duplicate dependency in same autoinclude
# unit "app" {
#   source = "./catalog/units/app"
#   path   = "app"
#   autoinclude {
#     dependency "vpc" { config_path = "../vpc" }
#     dependency "vpc" { config_path = "../other" }  # WARNING: Duplicate dependency block 'vpc' in autoinclude
#   }
# }

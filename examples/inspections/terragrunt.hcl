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
terraform {
  source           = "./modules/app"
  extra_arguments  = {}
}

# Unresolved variable reference
inputs = {
  x = local.does_not_exist
  y = dependency.nonexistent.outputs.id
}

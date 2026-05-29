# Entry point — find_in_parent_folders resolves directly from this file's location
locals {
  env = read_terragrunt_config(find_in_parent_folders("env.hcl"))
}

# Ctrl+B on "environment" resolves directly (no includer context needed)
inputs = {
  environment = local.env.locals.environment
}

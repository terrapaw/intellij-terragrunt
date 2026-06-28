include "root" {
  path = find_in_parent_folders("myconfig.hcl")
}

# Ctrl+B on "environment" navigates through the deep chain:
# include "root" → myconfig.hcl → env_config → env.hcl → environment
inputs = {
  env        = include.root.locals.env_config.locals.environment
  deploy_env = "${include.root.locals.deploy_env}-abc"
}

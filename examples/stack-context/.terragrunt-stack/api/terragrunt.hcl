include "root" {
  path = find_in_parent_folders("myconfig.hcl")
}

# Ctrl+B on "environment" should navigate to env.hcl locals block
# Today this fails because find_in_parent_folders("env.hcl") inside myconfig.hcl
# is evaluated from myconfig.hcl's parent instead of from .terragrunt-stack/api/
inputs = {
  env = include.root.locals.env_config.locals.environment
}

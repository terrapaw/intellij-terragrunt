locals {
  # find_in_parent_folders resolves from includer's directory (stack context)
  env_config  = read_terragrunt_config(find_in_parent_folders("env.hcl"))

  # get_parent_terragrunt_dir() returns this file's own directory (it's a parent config)
  # So this resolves to ./env.hcl — same directory as myconfig.hcl
  env_direct  = read_terragrunt_config("${get_parent_terragrunt_dir()}/env.hcl")

  app_name    = "my-api"
  deploy_env  = local.env_config.locals.environment
  deploy_reg  = local.env_direct.locals.region
}

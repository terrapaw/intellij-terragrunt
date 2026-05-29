locals {
  env_config  = read_terragrunt_config(find_in_parent_folders("env.hcl"))
  app_name    = "my-api"
  deploy_env  = local.env_config.locals.environment
}

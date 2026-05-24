include "root" {
  path   = find_in_parent_folders("root.hcl")
  expose = true
}

include "env" {
  path           = find_in_parent_folders("env.hcl")
  expose         = true
  merge_strategy = "no_merge"
}

locals {
  env_vars  = include.env.locals
  env_tags  = include.env.inputs.default_tags
  log_level = include.env.inputs.log_level
}

terraform {
  source = "tfr:///terraform-aws-modules/vpc/aws?version=5.0.0"
}

inputs = {
  name               = "${local.env_vars.environment}-vpc"
  cidr               = local.env_vars.vpc_cidr
  account_id         = include.root.locals.account_id
  region             = include.root.locals.aws_region
  azs                = ["us-east-1a", "us-east-1b", "us-east-1c"]
  private_subnets    = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets     = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
  enable_nat_gateway = true
  single_nat_gateway = true

  # For expression examples — try typing inside these for completion of loop variables
  subnet_names = [for idx, cidr in local.env_vars.vpc_cidr : "${local.env_vars.environment}-subnet-${idx}"]
  az_map       = {for az in ["us-east-1a", "us-east-1b"] : az => "${az}-zone"}
}

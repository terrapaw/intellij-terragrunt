include "root" {
  path = find_in_parent_folders("root.hcl")
}

dependency "vpc" {
  config_path = "../../vpc"

  mock_outputs = {
    vpc_id          = "vpc-mock-12345"
    private_subnets = ["subnet-mock-1", "subnet-mock-2"]
  }
}

dependency "app" {
  config_path = ".."

  mock_outputs = {
    security_group_id = "sg-mock-12345"
  }
}

# Example of read_terragrunt_config() pattern
locals {
  common = read_terragrunt_config(find_in_parent_folders("common.hcl"))
  org    = local.common.locals.org_name
  team   = local.common.locals.team
}

feature "multi_az" {
  default = false
}

generate "versions" {
  path      = "versions.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
EOF
}

terraform {
  source = "tfr:///terraform-aws-modules/rds/aws?version=6.1.0"
}

inputs = {
  identifier     = "my-app-db"
  engine         = "postgres"
  engine_version = "15.4"
  instance_class = feature.multi_az.value ? "db.r6g.large" : "db.t3.medium"
  multi_az       = feature.multi_az.value

  vpc_id                 = dependency.vpc.outputs.vpc_id
  subnet_ids             = dependency.vpc.outputs.private_subnets
  vpc_security_group_ids = [dependency.app.outputs.security_group_id]

  # Using read_terragrunt_config values
  tags = {
    Team        = local.team
    Org         = local.org
    CostCenter  = local.common.locals.cost_center
  }

  # Using inputs from read_terragrunt_config
  notification_email  = local.common.inputs.notification_email
  alert_slack_channel = local.common.inputs.alert_slack_channel
}

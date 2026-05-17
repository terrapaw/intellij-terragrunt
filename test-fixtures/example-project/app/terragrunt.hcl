include "root" {
  path = find_in_parent_folders("root.hcl")
}

dependency "vpc" {
  config_path = "../vpc"

  mock_outputs = {
    vpc_id          = "vpc-mock-12345"
    private_subnets = ["subnet-mock-1", "subnet-mock-2"]
  }
  mock_outputs_allowed_terraform_commands = ["validate", "plan"]
}

locals {
  app_name = "my-app"
  app_port = 8080
}

terraform {
  source = "${get_repo_root()}//modules/app"
}

inputs = {
  app_name        = local.app_name
  vpc_id          = dependency.vpc.outputs.vpc_id
  subnet_ids      = dependency.vpc.outputs.private_subnets
  container_port  = local.app_port
  environment     = "dev"
}

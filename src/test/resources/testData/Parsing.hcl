include "root" {
  path = find_in_parent_folders("root.hcl")
}

include "env" {
  path           = find_in_parent_folders("env.hcl")
  expose         = true
  merge_strategy = "no_merge"
}

dependency "vpc" {
  config_path = "../vpc"
  mock_outputs = {
    vpc_id          = "vpc-mock"
    private_subnets = ["subnet-1", "subnet-2"]
  }
  mock_outputs_allowed_terraform_commands = ["validate", "plan"]
}

feature "flag" {
  default = false
}

locals {
  name = "test"
  list = ["a", "b", "c"]
  map  = {
    key1 = "val1"
    key2 = "val2"
  }
  conditional = true ? "yes" : "no"
  func_call   = format("%s-%s", local.name, "suffix")
}

terraform {
  source = "${get_repo_root()}//modules/app"

  extra_arguments "vars" {
    commands = ["plan", "apply"]
    arguments = ["-var-file=terraform.tfvars"]
  }
}

inputs = {
  name       = local.name
  vpc_id     = dependency.vpc.outputs.vpc_id
  is_enabled = feature.flag.value
}

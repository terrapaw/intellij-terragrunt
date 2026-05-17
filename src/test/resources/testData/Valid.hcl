include "root" {
  path   = find_in_parent_folders("root.hcl")
  expose = true
}

dependency "vpc" {
  config_path = "../vpc"
  mock_outputs = {
    vpc_id = "vpc-123"
  }
}

locals {
  region = "us-east-1"
  name   = "my-app"
}

terraform {
  source = "git::git@github.com:example/modules.git//app?ref=v1.0"
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "overwrite"
  contents  = "provider \"aws\" { region = \"us-east-1\" }"
}

inputs = {
  vpc_id = dependency.vpc.outputs.vpc_id
  region = local.region
}

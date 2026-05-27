# Basic Terragrunt features: locals, dependencies, inputs, navigation, rename

locals {
  app_name = "my-app"
  app_port = 8080
  env      = "dev"
}

dependency "vpc" {
  config_path = "../vpc"

  mock_outputs = {
    vpc_id          = "vpc-mock-123"
    private_subnets = ["subnet-1", "subnet-2"]
  }
}

dependency "rds" {
  config_path = "../rds"

  mock_outputs = {
    endpoint = "db.example.com:5432"
  }
}

feature "canary" {
  default = false
}

terraform {
  source = "tfr:///terraform-aws-modules/ecs/aws?version=5.0.0"
}

inputs = {
  # Try: Ctrl+B on app_name → jumps to definition above
  # Try: Shift+F6 on app_name above → renames both usages
  name           = local.app_name
  service_name   = "${local.app_name}-service"
  port           = local.app_port

  # Try: Ctrl+B on vpc → jumps to dependency block
  # Try: dependency.vpc.outputs. → autocomplete shows vpc_id, private_subnets
  vpc_id         = dependency.vpc.outputs.vpc_id
  subnets        = dependency.vpc.outputs.private_subnets
  db_endpoint    = dependency.rds.outputs.endpoint

  # Try: Ctrl+B on value → jumps to default attribute
  canary_enabled = feature.canary.value
}

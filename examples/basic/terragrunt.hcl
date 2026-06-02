# Basic Terragrunt features: locals, dependencies, inputs, navigation, rename

locals {
  app_name = "my-app"
  app_port = 8080
  env      = "dev"

  # Object navigation — Ctrl+B works into nested keys
  config = {
    "network" = {
      "vpc_cidr" = "10.0.0.0/16"
      az_count = 3
    }
    tags = {
      team = "platform"
    }
  }
}

# Try: Ctrl+B on "vpc" label below → finds all dependency.vpc usages
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

  # For-expressions — try typing inside for loop variable completion
  subnet_names = [for idx, subnet in dependency.vpc.outputs.private_subnets : "${local.env}-subnet-${idx}"]
  az_map       = {for az in ["us-east-1a", "us-east-1b"] : az => "${az}-zone"}

  # Try: Ctrl+Q on merge for documentation popup
  tags = merge({Name = local.app_name}, {Env = local.env})

  # Try: Ctrl+B on network, vpc_cidr, or team → navigates into nested object keys
  vpc_cidr = local.config.network.vpc_cidr
  team     = local.config.tags.team
}

# Heredoc with interpolation — ${...} works inside heredocs too
generate "provider" {
  path      = "provider.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
provider "aws" {
  region = "${local.env}"

  default_tags {
    tags = {
      App = "${local.app_name}"
    }
  }
}
EOF
}

# Try also:
# - Ctrl+Q on a function name (e.g. merge) for documentation popup
# - Ctrl+Alt+L to auto-format the file
# - Type "dep" + Tab for live template expansion
# - Ctrl+J to see all available live templates


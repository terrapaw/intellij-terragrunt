# This file demonstrates autoinclude support.
# autoinclude injects dependencies between stack units without editing catalog sources.

unit "vpc" {
  source = "./catalog/units/vpc"
  path   = "vpc"
  values = {
    cidr = "10.0.0.0/16"
  }
}

unit "rds" {
  source = "./catalog/units/rds"
  path   = "rds"

  autoinclude {
    dependency "vpc" {
      config_path = unit.vpc.path

      mock_outputs = {
        vpc_id     = "vpc-mock-123"
        subnet_ids = ["subnet-a", "subnet-b"]
      }
      mock_outputs_allowed_terraform_commands = ["validate", "plan"]
    }

    inputs = {
      vpc_id     = dependency.vpc.outputs.vpc_id
      subnet_ids = dependency.vpc.outputs.subnet_ids
    }
  }
}

unit "app" {
  source = "./catalog/units/app"
  path   = "app"

  autoinclude {
    dependency "vpc" {
      config_path = unit.vpc.path

      mock_outputs = {
        vpc_id = "vpc-mock-123"
      }
    }

    dependency "rds" {
      config_path = unit.rds.path

      mock_outputs = {
        db_endpoint = "localhost:5432"
      }
    }

    inputs = {
      vpc_id      = dependency.vpc.outputs.vpc_id
      db_endpoint = dependency.rds.outputs.db_endpoint
      app_name    = "my-api"
    }
  }
}

# Stack-level autoinclude: injects a unit into a nested stack
stack "monitoring" {
  source = "./catalog/stacks/monitoring"
  path   = "monitoring"

  autoinclude {
    unit "extra-dashboard" {
      source = "./catalog/units/dashboard"
      path   = "extra-dashboard"
    }
  }
}

# This file demonstrates autoinclude-related inspections.

# Valid autoinclude — no warnings
unit "vpc" {
  source = "./catalog/units/vpc"
  path   = "vpc"
}

unit "app" {
  source = "./catalog/units/app"
  path   = "app"

  autoinclude {
    dependency "vpc" {
      config_path = unit.vpc.path
    }

    inputs = {
      vpc_id = dependency.vpc.outputs.vpc_id
    }
  }
}

# ERROR: locals blocks are not allowed in autoinclude
unit "bad_locals" {
  source = "./catalog/units/bad"
  path   = "bad-locals"

  autoinclude {
    locals {
      x = 1
    }
  }
}

# ERROR: values attribute is not allowed in autoinclude
unit "bad_values" {
  source = "./catalog/units/bad"
  path   = "bad-values"

  autoinclude {
    values = { x = 1 }
  }
}

# ERROR: Only one autoinclude block is allowed per unit/stack
unit "bad_multiple" {
  source = "./catalog/units/bad"
  path   = "bad-multiple"

  autoinclude {
    inputs = { x = 1 }
  }

  autoinclude {
    inputs = { y = 2 }
  }
}

# WARNING: Duplicate dependency block 'vpc' in autoinclude
unit "bad_dup_dep" {
  source = "./catalog/units/bad"
  path   = "bad-dup"

  autoinclude {
    dependency "vpc" {
      config_path = "../vpc"
    }
    dependency "vpc" {
      config_path = "../other-vpc"
    }
  }
}

# This unit's config is merged with the sibling terragrunt.autoinclude.hcl
# The autoinclude adds the vpc dependency and inputs.

terraform {
  source = "../../catalog/units/rds"
}

inputs = {
  engine  = "postgres"
  version = "15"
}

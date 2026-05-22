# This file is a unit source referenced by terragrunt.stack.hcl
# It uses values.X to reference data passed from the stack definition

terraform {
  source = "tfr:///terraform-aws-modules/vpc/aws?version=5.0.0"
}

inputs = {
  name               = values.vpc_name
  cidr               = values.cidr
  azs                = values.azs
  enable_nat_gateway = true
}

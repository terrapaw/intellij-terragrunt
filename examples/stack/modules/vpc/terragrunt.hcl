# Stack unit — uses values.X to reference data passed from terragrunt.stack.hcl
# Try: values. → autocomplete shows vpc_name, cidr, azs

terraform {
  source = "tfr:///terraform-aws-modules/vpc/aws?version=5.0.0"
}

inputs = {
  name               = values.vpc_name
  cidr               = values.cidr
  azs                = values.azs
  private_subnets    = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets     = ["10.0.101.0/24", "10.0.102.0/24"]
  enable_nat_gateway = true
}

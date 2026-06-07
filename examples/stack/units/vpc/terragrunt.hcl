# Stack unit — uses values.X to reference data passed from terragrunt.stack.hcl
# Try: Ctrl+B on vpc_name → jumps to definition in terragrunt.stack.hcl

terraform {
  source = "tfr:///terraform-aws-modules/vpc/aws?version=5.0.0"
}

inputs = {
  name = values.vpc_name
  cidr = values.cidr
  azs  = values.azs
}

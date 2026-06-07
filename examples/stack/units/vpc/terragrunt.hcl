terraform {
  source = "tfr:///terraform-aws-modules/vpc/aws?version=5.0.0"
}

inputs = {
  name = values.vpc_name
  cidr = values.cidr
  azs  = values.azs
}

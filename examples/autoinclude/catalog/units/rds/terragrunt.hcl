terraform {
  source = "tfr:///terraform-aws-modules/rds/aws?version=6.0.0"
}

inputs = {
  engine  = "postgres"
  version = "15"
}

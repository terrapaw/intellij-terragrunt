dependency "vpc" {
  config_path = "../vpc"
}

terraform {
  source = "tfr:///terraform-aws-modules/ecs/aws?version=5.0.0"
}

inputs = {
  cluster_name   = values.app_name
  container_port = values.port
  vpc_id         = dependency.vpc.outputs.vpc_id
  subnet_ids     = dependency.vpc.outputs.private_subnets
}

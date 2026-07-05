terraform {
  source = "tfr:///terraform-aws-modules/ecs/aws?version=5.0.0"
}

inputs = {
  app_name = "my-api"
}

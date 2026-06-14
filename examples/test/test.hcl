locals {
  abc = read_terragrunt_config("./root.hcl")
  a =  local.abc.locals.a
}
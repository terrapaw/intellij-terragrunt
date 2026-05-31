# Function-aware path resolution in includes and read_terragrunt_config
#
# Supported functions:
#   get_terragrunt_dir(), get_parent_terragrunt_dir(), get_root_terragrunt_dir()
#   get_repo_root(), get_path_to_repo_root(), get_path_from_repo_root()
#   find_in_parent_folders("X"), dirname(X), basename(X)

# find_in_parent_folders() is the correct way to locate parent configs in include paths
include "root" {
  path = find_in_parent_folders("root.hcl")
}

locals {
  # dirname(find_in_parent_folders("root.hcl")) returns the dir containing root.hcl
  shared = read_terragrunt_config("${dirname(find_in_parent_folders("root.hcl"))}/shared.hcl")

  # get_terragrunt_dir() returns the directory of THIS file
  local_config = read_terragrunt_config("${get_terragrunt_dir()}/local.hcl")

  # get_root_terragrunt_dir() finds the topmost dir with root.hcl
  root_config = read_terragrunt_config("${get_root_terragrunt_dir()}/root.hcl")

  # get_repo_root() finds the .git directory (absolute path)
  repo_config = read_terragrunt_config("${get_repo_root()}/examples/function-paths/shared.hcl")

  # get_path_to_repo_root() returns relative path to git root (e.g. "../..")
  # Used with full path from repo root — here it resolves to shared.hcl
  repo_relative = read_terragrunt_config("${get_path_to_repo_root()}/examples/function-paths/shared.hcl")

  # get_path_from_repo_root() returns path from git root to current dir (e.g. "examples/function-paths/app")
  repo_path_config = read_terragrunt_config("${get_repo_root()}/${get_path_from_repo_root()}/local.hcl")

  # basename(get_terragrunt_dir()) returns just the directory name (e.g. "app")
  basename_config = read_terragrunt_config("${get_parent_terragrunt_dir()}/${basename(get_terragrunt_dir())}/local.hcl")

  # Try: local.shared.locals. → autocomplete shows shared_vpc_cidr, shared_env
  cidr = local.shared.locals.shared_vpc_cidr
}

inputs = {
  # Navigation through dirname(find_in_parent_folders())
  # Try: Ctrl+B on shared_vpc_cidr → jumps to shared.hcl
  cidr = local.shared.locals.shared_vpc_cidr

  # Navigation through get_terragrunt_dir()
  # Try: Ctrl+B on app_name → jumps to local.hcl in same directory
  name = local.local_config.locals.app_name

  # Navigation through get_root_terragrunt_dir()
  # Try: Ctrl+B on root_region → jumps to root.hcl
  region = local.root_config.locals.root_region

  # Navigation through get_repo_root()
  # Try: Ctrl+B on shared_env → jumps to shared.hcl via repo root
  env = local.repo_config.locals.shared_env

  # Navigation through get_path_to_repo_root()
  # Try: Ctrl+B on shared_vpc_cidr → jumps to shared.hcl via relative path
  vpc = local.repo_relative.locals.shared_vpc_cidr

  # Navigation through get_path_from_repo_root()
  # Try: Ctrl+B on app_port → jumps to local.hcl
  port = local.repo_path_config.locals.app_port

  # Navigation through basename()
  # Try: Ctrl+B on app_name → jumps to local.hcl
  base_name = local.basename_config.locals.app_name
}

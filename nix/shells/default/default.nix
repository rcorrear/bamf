{
  # Snowfall Lib provides a customized `lib` instance with access to your flake's library
  # as well as the libraries available from your flake's inputs.
  lib,
  # You also have access to your flake's inputs.
  inputs,

  # The namespace used for your flake, defaulting to "internal" if not set.
  namespace,

  # All other arguments come from NixPkgs. You can use `pkgs` to pull shells or helpers
  # programmatically or you may add the named attributes as arguments here.
  pkgs,
  ...
}:
let
  my = rec {
    jdk23 = pkgs.jdk23.override { enableJavaFX = true; };
    polylith = pkgs.polylith.override { jdk = jdk23; };
  };
in

inputs.devenv.lib.mkShell {
  inherit inputs pkgs;
  modules = [
    (
      { pkgs, config, ... }:
      {
        git-hooks.hooks = {
          cljfmt.enable = true;
          deadnix.enable = true;
          flake-checker.enable = true;
          nixfmt-rfc-style.enable = true;
          shfmt.enable = true;
          statix.enable = true;
          trufflehog.enable = true;
          yamllint.enable = true;
        };

        languages = {
          clojure.enable = true;
          java = {
            enable = true;
            jdk.package = my.jdk23;
          };
        };

        packages = [
          my.jdk23
          my.polylith

          pkgs.clj-kondo
          pkgs.cljfmt
          pkgs.clojure-lsp
          pkgs.httpie
          pkgs.unzip
        ];

        services = {
          nginx = {
            enable = true;
            httpConfig =
              let
                upstream = "http://radarr.media.home.arpa:7878";
              in
              "
            server {
              listen 7878;

              resolver 127.0.0.53 [::1] valid=30s;

              location / {
                root ${pkgs.radarr}/share/radarr-${pkgs.radarr.version}/UI;
                try_files $uri $uri/index.html $uri.html =404;

                # replace __URL_BASE__
                sub_filter_once off;
                sub_filter __URL_BASE__ '';
              }

              location /content/ {
                root ${pkgs.radarr}/share/radarr-${pkgs.radarr.version}/UI/Content;
                try_files $uri =404;
              }

              location ~ ^/(initialize.json?[=0-9a-z]+)$ {
                proxy_pass ${upstream}/$1;
              }

              location ~ ^/(api/[0-9a-zA-Z/]+)$ {
                proxy_pass ${upstream}/$1;
              }

              location ~ ^/(MediaCover/[0-9a-zA-z?.=/\\-]+)$ {
                proxy_pass ${upstream}/$1;
              }
            }
            ";
          };
        };
      }
    )
  ];
}

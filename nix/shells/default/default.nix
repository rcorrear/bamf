{
  # You also have access to your flake's inputs.
  inputs,

  # All other arguments come from NixPkgs. You can use `pkgs` to pull shells or helpers
  # programmatically or you may add the named attributes as arguments here.
  pkgs,
  ...
}:
let
  my = rec {
    jdk25 = pkgs.jdk25.override { enableJavaFX = false; };
    polylith = pkgs.polylith.override { jdk = jdk25; };
  };
in

inputs.devenv.lib.mkShell {
  inherit inputs pkgs;
  modules = [
    (
      { pkgs, ... }:
      {
        git-hooks.hooks = {
          deadnix.enable = true;
          nixfmt-rfc-style.enable = true;
          shfmt.enable = true;
          statix.enable = true;
          trufflehog.enable = true;
          yamllint.enable = true;
          zizmor.enable = true;
          zprint = {
            enable = true;
            excludes = [ ".clj-kondo" ];
          };
        };

        languages = {
          clojure.enable = true;
          java = {
            enable = true;
            jdk.package = my.jdk25;
          };
        };

        packages = [
          my.jdk25
          my.polylith

          pkgs.babashka
          pkgs.bbin
          pkgs.clj-kondo
          pkgs.clojure-lsp
          pkgs.httpie
          pkgs.unzip
          pkgs.zprint
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

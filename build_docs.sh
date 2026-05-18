#!/bin/bash
set -e

# Configuration
PROJECT_NAME="jdivert"
GITHUB_USER="ffalcinelli"
# Use empty baseurl for local build to allow file:// preview
BASE_URL=""

# Paths
PAGES_SRC="_pages_src"
SITE_LOCAL="_site_local"
SITE_JAVADOCS="_site_javadocs"

# Clean up previous builds
rm -rf "$PAGES_SRC" "$SITE_LOCAL" "$SITE_JAVADOCS"

mkdir -p "$PAGES_SRC/docs"
mkdir -p "$PAGES_SRC/_layouts"
mkdir -p "$SITE_JAVADOCS/latest"

echo "Building latest (main)..."

# 1. Prepare main (latest)
printf -- "---\nlayout: default\ntitle: Home\n---\n\n" > "$PAGES_SRC/index.md"
cat README.md >> "$PAGES_SRC/index.md"

for f in docs/*.md; do
    filename=$(basename "$f")
    # Title from filename: architecture.md -> Architecture
    title=$(echo "$filename" | sed 's/\.[^.]*$//' | sed 's/.*/\L&/; s/[a-z]*/\u&/g')
    printf -- "---\nlayout: default\ntitle: $title\n---\n\n" > "$PAGES_SRC/docs/$filename"
    cat "$f" >> "$PAGES_SRC/docs/$filename"
done

mvn javadoc:javadoc
cp -r docs/api "$SITE_JAVADOCS/latest/"

# 2. Prepare Tags
TAGS=$(git tag -l "v*" | sort -rV)
OPTIONS="<option value=\"$BASE_URL/\">latest</option>"

for tag in $TAGS; do
    echo "Processing tag: $tag"
    # Create a temporary worktree for the tag
    WORKTREE_DIR="tmp_tags/$tag"
    mkdir -p "$WORKTREE_DIR"
    git worktree add "$WORKTREE_DIR" "$tag"
    
    pushd "$WORKTREE_DIR" > /dev/null
    mvn javadoc:javadoc || echo "Javadoc failed for $tag"
    popd > /dev/null
    
    # Setup Jekyll sources for the tag
    mkdir -p "$PAGES_SRC/$tag/docs"
    printf -- "---\nlayout: default\ntitle: Home ($tag)\n---\n\n" > "$PAGES_SRC/$tag/index.md"
    [ -f "$WORKTREE_DIR/README.md" ] && cat "$WORKTREE_DIR/README.md" >> "$PAGES_SRC/$tag/index.md"
    
    if [ -d "$WORKTREE_DIR/docs" ]; then
        for f in "$WORKTREE_DIR/docs"/*.md; do
            if [ -f "$f" ]; then
                filename=$(basename "$f")
                title=$(echo "$filename" | sed 's/\.[^.]*$//' | sed 's/.*/\L&/; s/[a-z]*/\u&/g')
                printf -- "---\nlayout: default\ntitle: $title ($tag)\n---\n\n" > "$PAGES_SRC/$tag/docs/$filename"
                cat "$f" >> "$PAGES_SRC/$tag/docs/$filename"
            fi
        done
    fi
    
    if [ -d "$WORKTREE_DIR/docs/api" ]; then
        mkdir -p "$SITE_JAVADOCS/$tag"
        cp -r "$WORKTREE_DIR/docs/api" "$SITE_JAVADOCS/$tag/"
    fi
    
    OPTIONS="$OPTIONS<option value=\"$BASE_URL/$tag/\">$tag</option>"
    
    # Clean up worktree
    git worktree remove "$WORKTREE_DIR"
done
rm -rf tmp_tags

# 3. Create Custom Layout
cat << 'EOF' > "$PAGES_SRC/_layouts/default.html"
<!DOCTYPE html>
<html lang="en-US">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{{ page.title }} | {{ site.title }}</title>
    <link rel="stylesheet" href="{{ "/assets/css/style.css" | relative_url }}">
    <style>
      .version-switcher { margin: 20px 0; padding: 10px; background: #f8f8f8; border-radius: 5px; }
      .version-switcher select { width: 100%; padding: 5px; margin-top: 5px; }
      .nav-menu { list-style: none; padding: 0; margin-top: 20px; }
      .nav-menu li { margin-bottom: 10px; }
      .nav-menu a { text-decoration: none; color: #1e6bb8; }
      .nav-menu a:hover { text-decoration: underline; }
    </style>
  </head>
  <body>
    <div class="wrapper">
      <header>
        <h1><a href="{{ "/" | absolute_url }}">{{ site.title }}</a></h1>
        <p>{{ site.description }}</p>
        
        <div class="version-switcher">
          <label for="version-select"><strong>Version:</strong></label>
          <select id="version-select" onchange="location = this.value;">
            __OPTIONS__
          </select>
        </div>

        {% assign v_prefix = "" %}
        {% assign url_parts = page.url | split: "/" %}
        {% if url_parts[1] != nil and url_parts[1] contains "v" %}
          {% assign v_prefix = "/" | append: url_parts[1] %}
        {% endif %}

        <ul class="nav-menu">
          <li><a href="{{ v_prefix | append: '/api/apidocs/index.html' | relative_url }}">Full API Reference (Javadoc)</a></li>
          <li><a href="{{ v_prefix | append: '/docs/architecture.html' | relative_url }}">Architecture Overview</a></li>
          <li><a href="{{ v_prefix | append: '/docs/filters.html' | relative_url }}">Filter Language Guide</a></li>
          <li><a href="{{ v_prefix | append: '/docs/examples.html' | relative_url }}">Examples Guide</a></li>
          <li><a href="{{ v_prefix | append: '/docs/performance.html' | relative_url }}">Performance Considerations</a></li>
          <li><a href="{{ v_prefix | append: '/docs/troubleshooting.html' | relative_url }}">Troubleshooting Guide</a></li>
          <li><a href="https://github.com/ffalcinelli/jdivert/blob/master/SECURITY.md">Security Policy</a></li>
        </ul>
      </header>
      <section>
        {{ content }}
      </section>
    </div>
    <script>
      var path = window.location.pathname;
      var select = document.getElementById("version-select");
      for (var i = 0; i < select.options.length; i++) {
        var val = select.options[i].value;
        if (val !== "/jdivert/" && path.indexOf(val) !== -1) {
          select.selectedIndex = i;
          break;
        }
      }
    </script>
  </body>
</html>
EOF

sed -i "s|__OPTIONS__|$OPTIONS|g" "$PAGES_SRC/_layouts/default.html"

# 4. Create _config.yml
cat <<EOF > "$PAGES_SRC/_config.yml"
theme: jekyll-theme-minimal
title: JDivert
description: Java bindings for WinDivert
baseurl: $BASE_URL
url: https://ffalcinelli.github.io
EOF

# 5. Build Site with Jekyll
export GEM_HOME=$HOME/.gem
export PATH=$HOME/.gem/ruby/3.4.0/bin:$PATH
jekyll build --source "$PAGES_SRC" --destination "$SITE_LOCAL"

# 6. Inject Javadocs
cp -r "$SITE_JAVADOCS/latest/api" "$SITE_LOCAL/"
for tagdir in "$SITE_JAVADOCS"/v*; do
    if [ -d "$tagdir" ]; then
        tag=$(basename "$tagdir")
        mkdir -p "$SITE_LOCAL/$tag"
        cp -r "$tagdir/api" "$SITE_LOCAL/$tag/"
    fi
done

echo "Done. Site is available in $SITE_LOCAL"

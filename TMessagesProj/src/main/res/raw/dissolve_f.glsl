#version 300 es

precision highp float;

in float alpha;
out vec4 fragColor;

uniform sampler2D u_Texture;
in vec2 texCoord;

void main() {
    vec4 textureColor = texture(u_Texture, texCoord);
    fragColor = vec4(textureColor.rgb, textureColor.a * alpha);
}
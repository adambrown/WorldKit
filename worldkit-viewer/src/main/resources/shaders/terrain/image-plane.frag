#version 330

uniform sampler2D imageTexture;
uniform bool isRgb;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    vec4 textureColor = texture(imageTexture, VertexIn.uv);
    colorOut = isRgb ? textureColor : vec4(textureColor.r, textureColor.r, textureColor.r, 1.0);
}

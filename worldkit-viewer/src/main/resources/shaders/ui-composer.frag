#version 330

uniform sampler2D imageTexture;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    colorOut = texture(imageTexture, VertexIn.uv);
}

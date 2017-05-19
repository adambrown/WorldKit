#version 330

uniform mat4 modelViewProjectionMatrix;

in VertexData {
    float height;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    if (!gl_FrontFacing) {
        colorOut = vec4(1.0f, 1.0f, 1.0f, 1.0f);
        return;
    }
    colorOut = vec4(VertexIn.height, VertexIn.height, VertexIn.height, 1.0f);
}

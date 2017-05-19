#version 330

uniform mat4 modelViewProjectionMatrix;

in vec3 position;

out VertexData {
    float height;
} VertexOut;

void main () {
    vec4 outPosition = modelViewProjectionMatrix * vec4(position, 1.0f);
    VertexOut.height = position.z;
    gl_Position = outPosition;
}
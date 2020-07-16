#version 330

uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

in vec2 position;
in vec2 uv;

out VertexData {
    vec3 cameraDir;
} VertexOut;

void main () {
    vec4 aPosition = vec4(position.xy, 0.0f, 1.0f);
    mat4 inverseProjection = inverse(projectionMatrix);
    mat3 inverseModelview = transpose(mat3(modelViewMatrix));
    vec3 unprojected = (inverseProjection * aPosition).xyz;
    VertexOut.cameraDir = inverseModelview * unprojected;

    gl_Position = aPosition;
}
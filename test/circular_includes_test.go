package test

import (
	"testing"

	"github.com/Workiva/frugal/compiler"
	"github.com/stretchr/testify/assert"
)

const circularFile = "idl/circular_1.frugal"

func TestCircularIncludes(t *testing.T) {
	options := compiler.Options{
		File:               circularFile,
		Gen:                "go",
		Out:                "out",
		Delim:              ".",
		DryRun:             true,
	}
	err := compiler.Compile(options)
	assert.Error(t, err)
	assert.Equal(
		t,
		"Include circular_2.frugal: Include circular_3.frugal: Include circular_1.frugal: Circular include: [circular_1 circular_2 circular_3 circular_1]",
		err.Error())
}
